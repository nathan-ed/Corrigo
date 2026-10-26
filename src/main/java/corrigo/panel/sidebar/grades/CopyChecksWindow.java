/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import corrigo.panel.sidebar.grades.export.CopyChecks;
import corrigo.panel.sidebar.grades.export.CopyChecks.Check;
import corrigo.panel.sidebar.grades.tags.ExerciseTags;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.interfaces.windows.AlternativeWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import java.io.File;
import java.util.*;

/**
 * What is left to check on all the copies of the evaluation (CopyChecks): copies not graded, lines not placed, grades
 * typed by hand (✎), sub-questions not graded. Enter (or a double click) opens the copy on the exercise, the text ready
 * to type, the list hidden; Ctrl+Shift+L brings it back, read again, on the next row.
 */
public class CopyChecksWindow extends AlternativeWindow<VBox> {

    private record Row(File pdf, Check check) {}

    private static CopyChecksWindow instance;

    private final Label summary = new Label();
    private final ListView<Row> rows = new ListView<>();

    // Opens the list, or brings it back read again
    public static void showChecks(){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        if(instance != null){ // Read again, at the row it was on
            instance.reload();
            if(!instance.isShowing()) instance.show();
            instance.toFront();
            instance.requestFocus();
            instance.rows.requestFocus();
            return;
        }
        instance = new CopyChecksWindow();
    }

    private CopyChecksWindow(){
        super(new VBox(), StageWidth.LARGE, TR.tr("checks.title"), TR.tr("checks.title"), TR.tr("checks.hint"));
        initModality(Modality.NONE); // The copies are opened while the list stays open
    }

    @Override
    public void setupSubClass(){
        summary.setWrapText(true);
        rows.setPrefHeight(420);
        rows.setPlaceholder(new Label(TR.tr("checks.none")));
        rows.setCellFactory(l -> new RowCell());
        rows.setOnMouseClicked(e -> {
            if(e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openSelected();
        });
        rows.setOnKeyPressed(e -> {
            if(e.getCode() == KeyCode.ENTER){
                e.consume();
                openSelected();
            }
        });
        VBox.setVgrow(rows, Priority.ALWAYS);
        root.setSpacing(8);
        root.setPadding(new Insets(10, 20, 10, 20));
        root.getChildren().addAll(summary, rows);

        Button reload = new Button(TR.tr("checks.reload"));
        reload.setOnAction(e -> reload());
        Button close = new Button(TR.tr("checks.close"));
        close.setOnAction(e -> close());
        setButtons(reload, close);
        reload();
    }

    @Override
    public void afterShown(){
        Platform.runLater(rows::requestFocus);
    }

    // Reads all the copies again (the open one saved first), the selection kept at its place: the next row once fixed
    private void reload(){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        MainWindow.gradingPanel.commitEdits();
        MainWindow.mainScreen.document.edition.save(false);
        List<File> pdfs = new ArrayList<>(ExerciseTags.getFolderCopies());
        pdfs.sort(Comparator.comparing(File::getName));
        ArrayList<Row> all = new ArrayList<>();
        Set<File> withChecks = new HashSet<>();
        for(File pdf : pdfs){
            File editFile = Edition.getEditFile(pdf);
            Map<String, Object> base = new LinkedHashMap<>();
            if(editFile.exists()){
                try{
                    Config config = new Config(editFile);
                    config.load();
                    base = config.base;
                }catch(Exception e){
                    Log.eNotified(e, "Unable to read " + editFile.getName());
                    continue;
                }
            }
            for(Check check : CopyChecks.of(base, PNFAnnotationManager.label())){
                all.add(new Row(pdf, check));
                withChecks.add(pdf);
            }
        }
        int selected = Math.max(0, rows.getSelectionModel().getSelectedIndex());
        rows.getItems().setAll(all);
        if(!all.isEmpty()){
            rows.getSelectionModel().select(Math.min(selected, all.size() - 1));
            rows.scrollTo(rows.getSelectionModel().getSelectedIndex());
        }
        summary.setText(all.isEmpty() ? TR.tr("checks.none") : TR.tr("checks.summary")
                .replace("{0}", String.valueOf(all.size())).replace("{1}", String.valueOf(withChecks.size()))
                .replace("{2}", String.valueOf(pdfs.size())));
    }

    private void openSelected(){
        Row row = rows.getSelectionModel().getSelectedItem();
        if(row == null) return;
        hide(); // Out of the way of the copy: Ctrl+Shift+L brings it back
        MainWindow.gradingPanel.openCopyAt(row.pdf(), Math.max(0, row.check().exercise()));
        Main.window.requestFocus(); // Back to the copy: what is typed goes to its text
    }

    private static String describe(Check check){
        String where = check.exerciseName() + (check.leaf().isEmpty() ? "" : " " + check.leaf());
        String what = switch(check.kind()){
            case NOT_STARTED -> TR.tr("checks.notStarted");
            case UNPLACED -> TR.tr("checks.unplaced").replace("{0}", String.valueOf(check.count()));
            case TYPED -> TR.tr("checks.typed").replace("{0}", MainWindow.gradesDigFormat.format(check.value()))
                    .replace("{1}", check.linesGrade() == null ? TR.tr("checks.linesNone") : MainWindow.gradesDigFormat.format(check.linesGrade()));
            case NOT_GRADED -> TR.tr("checks.notGraded");
        };
        return where.isEmpty() ? what : where + " : " + what;
    }

    private static class RowCell extends ListCell<Row> {
        @Override
        protected void updateItem(Row row, boolean empty){
            super.updateItem(row, empty);
            if(empty || row == null){
                setText(null);
                return;
            }
            String name = row.pdf().getName().replaceFirst("(?i)\\.pdf$", "");
            setText(name + "  —  " + describe(row.check()));
        }
    }
}
