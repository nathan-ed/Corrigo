/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeCopyGradeScaleDialog;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.style.Style;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Creation of the grade scale of a new evaluation: the number of exercises, then for each its name, its page, its
 * sub-questions and their points. The grade of each exercise is placed on its page (top right, its sub-questions under
 * it): the pages of the exercises are where their grades are. The grade tab stays the place to change the scale.
 */
public final class GradeScaleSetupDialog {

    private static final int MAX_EXERCISES = 30;
    private static final int MAX_SUB_QUESTIONS = 10;
    // Where the grades are placed on their page, in percent of the page: the right margin, from the top
    private static final double GRADE_X = .79, FIRST_Y = .07, STEP_Y = .06;

    private final int pagesNumber;
    private final VBox rows = new VBox(6);
    private final List<Row> rowList = new ArrayList<>();
    private final Label total = new Label();
    private final Dialog<ButtonType> dialog = new Dialog<>();
    private ButtonType create;

    private GradeScaleSetupDialog(int pagesNumber){
        this.pagesNumber = pagesNumber;
    }

    // Opens the dialog if a document is open and has no grade scale yet. Returns true if the scale was created.
    public static boolean show(){
        if(!MainWindow.mainScreen.hasDocument(false)) return false;
        if(GradeTreeView.getTotal() != null && !GradeTreeView.getTotal().getChildren().isEmpty()) return false;
        return new GradeScaleSetupDialog(MainWindow.mainScreen.document.getPagesNumber()).open();
    }

    // One exercise: its name, page, sub-questions and their points
    private final class Row {
        final TextField name = new TextField();
        final Spinner<Integer> page = new Spinner<>(1, Math.max(1, pagesNumber), 1);
        final Spinner<Integer> subQuestions = new Spinner<>(0, MAX_SUB_QUESTIONS, 0);
        final HBox points = new HBox(6);
        final List<TextField> pointFields = new ArrayList<>();
        final GridPane grid;
        final int index;

        Row(int index, GridPane grid, String defaultName, int defaultPage){
            this.index = index;
            this.grid = grid;
            name.setText(defaultName);
            name.setPrefColumnCount(7);
            page.getValueFactory().setValue(Math.clamp(defaultPage, 1, Math.max(1, pagesNumber)));
            page.setPrefWidth(76);
            page.setEditable(true);
            subQuestions.setPrefWidth(76);
            subQuestions.setEditable(true);
            points.setAlignment(Pos.CENTER_LEFT);
            subQuestions.valueProperty().addListener((o, oldValue, newValue) -> buildPoints());
            name.textProperty().addListener((o, oldValue, newValue) -> validate());
            buildPoints();
            grid.addRow(index + 1, name, page, subQuestions, points);
        }

        // One points field for the exercise, or one per sub-question (a, b, c…)
        void buildPoints(){
            List<String> previous = pointFields.stream().map(TextField::getText).toList();
            pointFields.clear();
            points.getChildren().clear();
            int count = subQuestions.getValue();
            for(int i = 0; i < Math.max(1, count); i++){
                TextField field = new TextField(i < previous.size() ? previous.get(i) : "");
                field.setPrefColumnCount(3);
                field.setPromptText(TR.tr("gradeScaleSetup.points.prompt"));
                field.textProperty().addListener((o, oldValue, newValue) -> validate());
                pointFields.add(field);
                if(count > 0){
                    Label label = new Label(subQuestionName(i));
                    label.setMinWidth(Region.USE_PREF_SIZE);
                    points.getChildren().add(label);
                }
                points.getChildren().add(field);
            }
            validate();
        }

        String getName(){
            return name.getText().strip();
        }
        List<Double> getPoints(){
            return pointFields.stream().map(field -> parse(field.getText())).toList();
        }
    }

    private boolean open(){
        dialog.initOwner(MainWindow.mainScreen.getScene().getWindow());
        dialog.setTitle(TR.tr("gradeScaleSetup.title"));
        dialog.setHeaderText(TR.tr("gradeScaleSetup.header"));

        Spinner<Integer> count = new Spinner<>(1, MAX_EXERCISES, 3);
        count.setEditable(true);
        count.setPrefWidth(80);
        Label countLabel = new Label(TR.tr("gradeScaleSetup.count"));
        HBox countBox = new HBox(10, countLabel, count);
        countBox.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        String[] headers = {"gradeScaleSetup.column.name", "gradeScaleSetup.column.page", "gradeScaleSetup.column.subQuestions", "gradeScaleSetup.column.points"};
        for(int i = 0; i < headers.length; i++){
            Label header = new Label(TR.tr(headers[i]));
            header.setStyle("-fx-font-weight: bold;");
            header.setMinWidth(Region.USE_PREF_SIZE);
            grid.add(header, i, 0);
        }
        rows.getChildren().add(grid);
        count.valueProperty().addListener((o, oldValue, newValue) -> buildRows(grid, newValue));

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(300);
        scroll.setStyle("-fx-background-color: transparent;");

        total.setStyle("-fx-font-weight: bold;");
        CheckBox copyToOthers = new CheckBox(TR.tr("gradeScaleSetup.copyToOthers"));
        long others = MainWindow.filesTab.getOpenedFiles().stream()
                .filter(file -> !file.equals(MainWindow.mainScreen.document.getFile()))
                .filter(file -> file.getParent() != null && file.getParent().equals(MainWindow.mainScreen.document.getFile().getParent()))
                .count();
        copyToOthers.setSelected(others > 0);
        copyToOthers.setDisable(others == 0);
        Label help = new Label(TR.tr("gradeScaleSetup.help"));
        help.setWrapText(true);
        help.setStyle("-fx-opacity: .7; -fx-font-size: 11;");

        VBox content = new VBox(12, countBox, scroll, total, copyToOthers, help);
        content.setPadding(new Insets(10));
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);
        create = new ButtonType(TR.tr("gradeScaleSetup.create"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(TR.tr("actions.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, create);
        dialog.setResizable(true);
        StyleManager.putStyle(dialog.getDialogPane(), Style.DEFAULT);

        buildRows(grid, count.getValue());
        Platform.runLater(() -> {
            count.requestFocus();
            count.getEditor().selectAll();
        });

        if(dialog.showAndWait().orElse(cancel) != create) return false;
        createGrades();
        if(copyToOthers.isSelected()){
            int copied = new GradeCopyGradeScaleDialog().copyToSameFolder(true);
            MainWindow.filesTab.refresh();
            MainWindow.footerBar.showToast(javafx.scene.paint.Color.web("#1b5e20"), javafx.scene.paint.Color.WHITE,
                    TR.tr("gradeScaleSetup.done.copied", String.valueOf(copied)));
        }else{
            MainWindow.footerBar.showToast(javafx.scene.paint.Color.web("#1b5e20"), javafx.scene.paint.Color.WHITE, TR.tr("gradeScaleSetup.done"));
        }
        return true;
    }

    // Keeps what was typed in the rows that stay
    private void buildRows(GridPane grid, int count){
        while(rowList.size() > count){
            Row last = rowList.removeLast();
            grid.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) == last.index + 1);
        }
        while(rowList.size() < count){
            int index = rowList.size();
            rowList.add(new Row(index, grid, TR.tr("gradeScaleSetup.defaultName", String.valueOf(index + 1)), guessPage(index, count)));
        }
        validate();
    }

    // Exercises one per page, the first pages being a cover: the last pages of the copy
    private int guessPage(int index, int count){
        int first = Math.max(1, pagesNumber - count + 1);
        return Math.min(pagesNumber, first + index);
    }

    private void validate(){
        if(create == null) return;
        boolean valid = !rowList.isEmpty();
        HashSet<String> names = new HashSet<>();
        double sum = 0;
        for(Row row : rowList){
            boolean nameValid = !row.getName().isEmpty() && !row.getName().contains("\\") && names.add(row.getName());
            row.name.setStyle(nameValid ? "" : "-fx-border-color: #c62828;");
            valid &= nameValid;
            for(int i = 0; i < row.pointFields.size(); i++){
                Double points = row.getPoints().get(i);
                boolean pointsValid = points != null && points > 0;
                row.pointFields.get(i).setStyle(pointsValid || row.pointFields.get(i).getText().isBlank() ? "" : "-fx-border-color: #c62828;");
                valid &= pointsValid;
                if(pointsValid) sum += points;
            }
        }
        total.setText(TR.tr("gradeScaleSetup.total", MainWindow.gradesDigFormat.format(sum)));
        dialog.getDialogPane().lookupButton(create).setDisable(!valid);
    }

    // The grades: the exercises under the total, their sub-questions under them, on the page of the exercise
    private void createGrades(){
        if(GradeTreeView.getTotal() == null) MainWindow.gradeTab.treeView.clearElements(true, false);
        GradeTreeItem totalItem = GradeTreeView.getTotal();
        String totalPath = GradeTreeView.getElementPath(totalItem);
        MainWindow.mainScreen.setSelected(null);
        for(Row row : rowList){
            PageRenderer page = MainWindow.mainScreen.document.getPage(Math.clamp(row.page.getValue() - 1, 0, pagesNumber - 1));
            List<Double> points = row.getPoints();
            boolean hasSubQuestions = row.subQuestions.getValue() > 0;
            double exerciseTotal = points.stream().mapToDouble(Double::doubleValue).sum();
            addGrade(page, 0, row.getName(), hasSubQuestions ? 0 : exerciseTotal, row.index, totalPath);
            if(!hasSubQuestions) continue;
            String exercisePath = totalPath + "\\" + row.getName();
            for(int i = 0; i < points.size(); i++){
                addGrade(page, i + 1, subQuestionName(i), points.get(i), i, exercisePath);
            }
        }
        // The totals of the exercises, then of the evaluation
        for(var exercise : GradeTreeView.getTotal().getChildren()) ((GradeTreeItem) exercise).makeSum(false);
        GradeTreeView.getTotal().makeSum(false);
        // The total at the top right of the first page, like the exercises on theirs
        GradeElement totalGrade = GradeTreeView.getTotal().getCore();
        if(totalGrade.getPageNumber() != 0) totalGrade.switchPage(0);
        totalGrade.setRealX((int) (Element.GRID_WIDTH * GRADE_X));
        totalGrade.setRealY((int) (Element.GRID_HEIGHT * FIRST_Y));
        totalGrade.setAlwaysVisible(true, false);
        
        // Grading starts with the first exercise, at its page
        MainWindow.footerBar.refreshExerciseChoices();
        MainWindow.footerBar.setSelectedExerciseKey(MainWindow.footerBar.getExerciseKey(0));
        MainWindow.footerBar.reloadGradingPanel();
        MainWindow.footerBar.navigateToSelectedExercisePage();
    }

    private static void addGrade(PageRenderer page, int line, String name, double total, int index, String parentPath){
        GradeElement grade = new GradeElement((int) (Element.GRID_WIDTH * GRADE_X), (int) (Element.GRID_HEIGHT * (FIRST_Y + line * STEP_Y)),
                page.getPage(), true, -1, total, -1, index, parentPath, name, true);
        page.addElement(grade, true, UType.ELEMENT);
    }

    private static String subQuestionName(int index){
        return String.valueOf((char) ('a' + index));
    }

    private static Double parse(String text){
        String value = text.strip().replace(',', '.');
        if(value.isEmpty()) return null;
        try{
            return Double.parseDouble(value);
        }catch(NumberFormatException e){
            return null;
        }
    }
}
