/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.datasaving.simpleconfigs.ExerciseCorrectionData;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.Marks.Position;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * Writes the mark (see Marks), the value only, on all the graded copies of the files list, whatever their evaluation,
 * at the same position. Then lists the copies for which a few more points would change the mark.
 */
public class MarksComputation {

    private record Result(File file, double value, double total, double mark, OptionalDouble raise) {}

    private static Position storedPosition;

    private MarksComputation(){
    }

    public static Position getStoredPosition(){
        return storedPosition;
    }
    public static void setStoredPosition(Position position){
        storedPosition = position;
    }
    private static void storePosition(Position position){
        if(position.equals(storedPosition)) return;
        storedPosition = position;
        ExerciseCorrectionData.requestSave();
    }

    // The mark of the open document, if any.
    public static TextElement findMarkElement(){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            for(var element : page.getElements()){
                if(element instanceof TextElement text && text.isMark()) return text;
            }
        }
        return null;
    }

    // Stores the position of the marks, and moves (or writes) the mark of the open document there.
    public static void setPosition(PageRenderer page, int x, int y){
        Position position = new Position(page.getPage(), x, y);
        storePosition(position);
        TextElement mark = updateOpenDocument(position);
        if(mark != null) MainWindow.mainScreen.setSelected(mark);
    }

    /**
     * Computes the marks of all the files of the files list, whatever their evaluation.
     * The mark of the open document gives the position (it may have been moved), else the stored position.
     * @return false if the position is not known yet: nothing was done.
     */
    public static boolean computeAll(){
        return computeAll(true);
    }
    // Updates the marks written on the copies, without showing the results. False if the position of the marks is not known yet.
    public static boolean recomputeWrittenMarks(){
        return computeAll(false);
    }
    private static boolean computeAll(boolean showResults){
        if(!MainWindow.mainScreen.hasDocument(false) || GradeTreeView.getTotal() == null) return true;
        TextElement current = findMarkElement();
        Position position = current != null ? new Position(current.getPageNumber(), current.getRealX(), current.getRealY()) : storedPosition;
        if(position == null) return false;
        storePosition(position);

        ArrayList<Result> results = new ArrayList<>();
        ArrayList<File> ungraded = new ArrayList<>();

        File currentFile = MainWindow.mainScreen.document.getFile();
        if(updateOpenDocument(position) != null){
            GradeElement total = GradeTreeView.getTotal().getCore();
            results.add(toResult(currentFile, total.getValue(), total.getTotal()));
        }else if(!GradeTreeView.getTotal().getChildren().isEmpty()) ungraded.add(currentFile); // Else: no grade scale

        for(File file : MainWindow.filesTab.getOpenedFiles()){
            if(file.equals(currentFile)) continue;
            File editFile = Edition.getEditFile(file);
            if(!editFile.exists()) continue;
            try{
                Config config = new Config(editFile);
                config.load();
                if(ScoredCommentGrades.getGrades(config.base).isEmpty()) continue; // No grade scale

                double[] root = ScoredCommentGrades.getRootGrade(config.base);
                boolean graded = root != null && root[0] >= 0 && Marks.isGraded(config.base, GradeElement::isBonus);
                if(graded){
                    Result result = toResult(file, root[0], root[1]);
                    Marks.setMarkText(config.base, position.page(), buildTextData(position, result.mark()));
                    results.add(result);
                }else{
                    ungraded.add(file);
                    // A mark written before is no longer right
                    if(Marks.removeMarkTexts(config.base) == 0) continue;
                }
                config.save();
                Edition.removePreloadedEditFile(editFile);
            }catch(Exception e){
                Log.eNotified(e, "Unable to compute the mark of " + file.getName());
            }
        }
        MainWindow.filesTab.refresh();
        if(showResults) showResults(results, ungraded);
        return true;
    }

    private static Result toResult(File file, double value, double total){
        return new Result(file, value, total, Marks.compute(value, total), Marks.getChangingRaise(value, total));
    }

    // OPEN DOCUMENT

    // Writes the mark of the open document at this position, or removes it if the copy is not graded. Returns the mark, if any.
    private static TextElement updateOpenDocument(Position position){
        TextElement mark = findMarkElement();
        if(!isOpenDocumentGraded()){
            if(mark != null) mark.delete(true, UType.ELEMENT);
            return null;
        }
        GradeElement total = GradeTreeView.getTotal().getCore();
        String text = formatText(Marks.compute(total.getValue(), total.getTotal()));
        int pageIndex = Math.clamp(position.page(), 0, MainWindow.mainScreen.document.getPagesNumber() - 1);

        if(mark == null){
            TiersFont style = GradeTab.fontTiers.get(0);
            mark = new TextElement(position.x(), position.y(), pageIndex, true, text, style.getColor(), style.getFont(), 0);
            mark.setMark(true);
            MainWindow.mainScreen.document.getPage(pageIndex).addElement(mark, true, UType.ELEMENT);
        }else{
            if(!mark.getText().equals(text)) mark.setText(text);
            if(mark.getPageNumber() != pageIndex) mark.switchPage(pageIndex);
            if(mark.getRealX() != position.x()) mark.setRealX(position.x());
            if(mark.getRealY() != position.y()) mark.setRealY(position.y());
            Edition.setUnsave("Mark computed"); // Changing the text of an element does not do it
        }
        return mark;
    }

    private static boolean isOpenDocumentGraded(){
        GradeTreeItem total = GradeTreeView.getTotal();
        if(total.getCore().getValue() < 0) return false;
        return GradeTreeView.getGradesArray(total).stream()
                .filter(item -> !item.hasSubGrade() && !item.getCore().isBonus())
                .allMatch(item -> item.getCore().getValue() >= 0);
    }

    // Text data of the mark of another copy, same style as the open document.
    private static LinkedHashMap<Object, Object> buildTextData(Position position, double mark){
        TiersFont style = GradeTab.fontTiers.get(0);
        TextElement text = new TextElement(position.x(), position.y(), position.page(), false, formatText(mark), style.getColor(), style.getFont(), 0);
        return text.getYAMLData();
    }

    private static String formatText(double mark){
        return MainWindow.gradesDigFormat.format(mark);
    }

    // RESULTS

    private static void showResults(List<Result> results, List<File> ungraded){
        // Copies of several evaluations: their folder is shown
        boolean severalFolders = Stream.concat(results.stream().map(Result::file), ungraded.stream())
                .map(File::getParentFile).distinct().count() > 1;
        Comparator<File> byPath = Comparator.comparing((File file) -> file.getParentFile().getName()).thenComparing(File::getName);
        List<Result> close = results.stream()
                .filter(result -> result.raise().isPresent())
                .sorted(Comparator.comparingDouble((Result result) -> result.raise().getAsDouble()).thenComparing(Result::file, byPath))
                .toList();
        List<Result> all = results.stream().sorted(Comparator.comparing(Result::file, byPath)).toList();

        CustomAlert dialog = new CustomAlert(Alert.AlertType.INFORMATION, TR.tr("marks.results.title"), TR.tr("marks.results.header", results.size()));
        dialog.initModality(Modality.NONE);

        ListView<Result> list = new ListView<>();
        list.getItems().setAll(close);
        list.setCellFactory(view -> new ListCell<>(){
            @Override
            protected void updateItem(Result result, boolean empty){
                super.updateItem(result, empty);
                setText(empty || result == null ? null : formatResult(result, severalFolders));
            }
        });
        list.setPlaceholder(new Label(TR.tr("marks.results.noneClose")));
        list.setOnMouseClicked(e -> {
            Result selected = list.getSelectionModel().getSelectedItem();
            if(e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && selected != null) MainWindow.mainScreen.openFile(selected.file());
        });
        list.setPrefSize(620, 320);

        CheckBox onlyClose = new CheckBox(TR.tr("marks.results.onlyClose", close.size()));
        onlyClose.setSelected(true);
        onlyClose.selectedProperty().addListener((o, oldValue, newValue) -> list.getItems().setAll(newValue ? close : all));

        Label details = new Label(TR.tr("marks.results.details"));
        details.setWrapText(true);
        details.setMaxWidth(620);
        VBox content = new VBox(8, details, onlyClose, list);
        if(!ungraded.isEmpty()){
            ListView<File> ungradedList = new ListView<>();
            ungradedList.getItems().setAll(ungraded.stream().sorted(byPath).toList());
            ungradedList.setCellFactory(view -> new ListCell<>(){
                @Override
                protected void updateItem(File file, boolean empty){
                    super.updateItem(file, empty);
                    setText(empty || file == null ? null : getDisplayName(file, severalFolders));
                }
            });
            ungradedList.setOnMouseClicked(e -> {
                File selected = ungradedList.getSelectionModel().getSelectedItem();
                if(e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && selected != null) MainWindow.mainScreen.openFile(selected);
            });
            ungradedList.setPrefSize(620, Math.min(150, 30 + ungraded.size() * 26));
            content.getChildren().addAll(new Label(TR.tr("marks.results.ungraded", ungraded.size())), ungradedList);
        }
        dialog.getDialogPane().setContent(content);
        dialog.addOKButton(ButtonPosition.DEFAULT);
        dialog.show();

        MainWindow.footerBar.showToast(Color.web("#1b5e20"), Color.WHITE, TR.tr("marks.done", results.size()));
    }

    // "Name — 17.5 / 24 — 4.5 — +0.5 pt → 5"
    private static String formatResult(Result result, boolean severalFolders){
        String line = getDisplayName(result.file(), severalFolders) + "  —  " + MainWindow.gradesDigFormat.format(result.value()) + " / " + MainWindow.gradesDigFormat.format(result.total())
                + "  —  " + MainWindow.gradesDigFormat.format(result.mark());
        if(result.raise().isPresent()){
            double raise = result.raise().getAsDouble();
            line += "  —  " + TR.tr("marks.results.raise", MainWindow.gradesDigFormat.format(raise),
                    MainWindow.gradesDigFormat.format(Marks.compute(result.value() + raise, result.total())));
        }
        return line;
    }
    
    private static String getDisplayName(File file, boolean withFolder){
        return withFolder ? file.getParentFile().getName() + " / " + file.getName() : file.getName();
    }
}
