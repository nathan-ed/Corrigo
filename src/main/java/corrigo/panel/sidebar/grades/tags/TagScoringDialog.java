/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import corrigo.panel.sidebar.grades.tags.EvaluationTags.Kind;
import corrigo.panel.sidebar.grades.tags.EvaluationTags.Tag;
import corrigo.panel.sidebar.grades.tags.EvaluationTags.Target;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.*;

/**
 * Points and comment of a method or a mistake: a method adds its points, a mistake removes them. They count on the
 * sub-grade the occurrence is put for, or on chosen sub-grades of the exercise, each with its points.
 * What is written on the copy is shown while typing.
 */
public final class TagScoringDialog {

    private TagScoringDialog(){
    }

    // A sub-grade of the exercise: its path, its name ("a", "a › i") and its points field
    private record SubGrade(String path, String name, TextField points) {}

    /**
     * @param exerciseName the exercise being graded: its sub-grades can be chosen (null: only the points of the
     *                     sub-grade the occurrence is put for)
     */
    public static void show(Tag tag, String exerciseName, Window owner){
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner != null ? owner : MainWindow.mainScreen.getScene().getWindow());
        dialog.setTitle(TR.tr("tags.scoring.title"));
        dialog.setHeaderText(tag.getName() + "  ·  " + TR.tr(tag.getKind() == Kind.METHOD ? "tags.kind.method" : "tags.kind.mistake"));

        TextField points = new TextField(tag.getPoints() == null ? "" : MainWindow.gradesDigFormat.format(tag.getPoints()));
        points.setPromptText(TR.tr("tags.scoring.points.prompt"));
        points.setPrefColumnCount(5);
        points.setMaxWidth(Region.USE_PREF_SIZE);
        TextField comment = new TextField(tag.getComment() == null ? "" : tag.getComment());
        comment.setPromptText(TR.tr("tags.scoring.comment.prompt"));
        comment.setPrefColumnCount(28);
        GridPane.setHgrow(comment, Priority.ALWAYS);

        Label pointsHelp = new Label(TR.tr(tag.getKind() == Kind.METHOD ? "tags.scoring.points.method" : "tags.scoring.points.mistake"));
        pointsHelp.setStyle("-fx-opacity: .7; -fx-font-size: 11;");
        pointsHelp.setWrapText(true);

        // Where the points count: the sub-grade the occurrence is put for, or these sub-grades of the exercise
        List<SubGrade> subGrades = getSubGrades(exerciseName);
        Map<String, Double> current = exerciseName == null ? Map.of() : tag.getGradePoints(exerciseName);
        ToggleGroup where = new ToggleGroup();
        RadioButton onPut = new RadioButton(TR.tr("tags.scoring.onPut"));
        RadioButton onChosen = new RadioButton(TR.tr("tags.scoring.onChosen", exerciseName == null ? "" : exerciseName));
        onPut.setToggleGroup(where);
        onChosen.setToggleGroup(where);
        (current.isEmpty() ? onPut : onChosen).setSelected(true);
        GridPane chosen = new GridPane();
        chosen.setHgap(8);
        chosen.setVgap(4);
        chosen.setPadding(new Insets(0, 0, 0, 26));
        for(SubGrade subGrade : subGrades){
            Double value = current.get(subGrade.path());
            subGrade.points().setText(value == null ? "" : MainWindow.gradesDigFormat.format(value));
            subGrade.points().setPromptText(TR.tr("tags.scoring.points.prompt"));
            subGrade.points().setPrefColumnCount(5);
            Label name = new Label(subGrade.name());
            name.setMinWidth(Region.USE_PREF_SIZE);
            chosen.addRow(chosen.getRowCount(), name, subGrade.points());
        }
        onPut.setWrapText(true);
        onChosen.setWrapText(true);
        VBox putRow = new VBox(4, points, pointsHelp);
        putRow.setPadding(new Insets(0, 0, 0, 26));
        VBox whereBox = new VBox(6, onPut, putRow, onChosen, chosen);
        // Only one sub-grade (or no exercise): the points of the sub-grade it is put for only
        boolean canChoose = subGrades.size() > 1;
        onChosen.setVisible(canChoose);
        onChosen.setManaged(canChoose);
        chosen.setVisible(canChoose);
        chosen.setManaged(canChoose);
        if(!canChoose) onPut.setSelected(true);

        Label preview = new Label();
        preview.setWrapText(true);
        preview.setStyle("-fx-font-weight: bold;");
        int uses = ExerciseTags.getData().getUses(tag).size();
        Label applies = new Label(uses == 0 ? TR.tr("tags.scoring.where.none") : uses == 1 ? TR.tr("tags.scoring.where.one") : TR.tr("tags.scoring.where", String.valueOf(uses)));
        applies.setWrapText(true);
        applies.setStyle("-fx-opacity: .7; -fx-font-size: 11;");

        Label pointsLabel = new Label(TR.tr("tags.scoring.points"));
        Label commentLabel = new Label(TR.tr("tags.scoring.comment"));
        for(Label label : new Label[]{pointsLabel, commentLabel}) label.setMinWidth(Region.USE_PREF_SIZE);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, pointsLabel, whereBox);
        grid.addRow(1, commentLabel, comment);
        GridPane.setValignment(pointsLabel, javafx.geometry.VPos.TOP);
        VBox content = new VBox(12, grid, preview, applies);
        content.setPadding(new Insets(10));
        content.setPrefWidth(500);
        dialog.getDialogPane().setContent(content);
        ButtonType ok = new ButtonType(TR.tr("actions.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(TR.tr("actions.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, ok);
        StyleManager.putStyle(dialog.getDialogPane(), fr.clementgre.pdf4teachers.utils.style.Style.DEFAULT);

        Runnable update = () -> {
            boolean byGrade = onChosen.isSelected();
            points.setDisable(byGrade);
            subGrades.forEach(subGrade -> subGrade.points().setDisable(!byGrade));
            boolean valid = true;
            for(TextField field : allFields(points, subGrades)){
                boolean fieldValid = field.getText().isBlank() || isNumber(field.getText()); // 0: no points
                field.setStyle(fieldValid ? "" : "-fx-border-color: #c62828;");
                valid &= fieldValid;
            }
            dialog.getDialogPane().lookupButton(ok).setDisable(!valid);

            // What an occurrence would write, put for the active sub-grade
            EvaluationTags sample = new EvaluationTags();
            Tag copy = sample.create(tag.getExercise(), tag.getName(), tag.getKind());
            sample.setScoring(copy, byGrade ? null : parse(points.getText()), comment.getText());
            if(byGrade && exerciseName != null) sample.setGradePoints(copy, exerciseName, getChosen(subGrades));
            String exercise = exerciseName == null ? tag.getExercise() : exerciseName;
            List<Target> targets = sample.getTargets(sample.add("", exercise, copy, ExerciseTags.getTargetGrade(exercise), EvaluationTags.Placement.exercise(0)));
            if(targets.isEmpty()){
                preview.setText(TR.tr("tags.scoring.preview.none"));
            }else{
                StringJoiner written = new StringJoiner("   ·   ");
                for(Target target : targets){
                    String text = TagEditFiles.render(target, MainWindow.gradesDigFormat);
                    written.add(byGrade ? nameOf(subGrades, target.grade()) + " : " + text : text);
                }
                preview.setText(TR.tr("tags.scoring.preview", written.toString()));
            }
        };
        for(TextField field : allFields(points, subGrades)) field.textProperty().addListener((o, oldValue, newValue) -> update.run());
        comment.textProperty().addListener((o, oldValue, newValue) -> update.run());
        where.selectedToggleProperty().addListener((o, oldValue, newValue) -> {
            if(newValue == null) where.selectToggle(oldValue);
            update.run();
        });
        update.run();
        Platform.runLater(() -> (onChosen.isSelected() && !subGrades.isEmpty() ? subGrades.getFirst().points() : points).requestFocus());

        if(dialog.showAndWait().orElse(cancel) != ok) return;
        if(onChosen.isSelected() && exerciseName != null){
            // The points by sub-grade replace the single amount for this exercise (kept for the other exercises)
            ExerciseTags.setScoring(tag, parse(points.getText()), comment.getText(), exerciseName, getChosen(subGrades));
        }else{
            ExerciseTags.setScoring(tag, parse(points.getText()), comment.getText(), exerciseName, Map.of());
        }
    }

    private static List<TextField> allFields(TextField points, List<SubGrade> subGrades){
        ArrayList<TextField> fields = new ArrayList<>(List.of(points));
        subGrades.forEach(subGrade -> fields.add(subGrade.points()));
        return fields;
    }
    // Points typed for the sub-grades, in the order of the grade scale
    private static LinkedHashMap<String, Double> getChosen(List<SubGrade> subGrades){
        LinkedHashMap<String, Double> chosen = new LinkedHashMap<>();
        for(SubGrade subGrade : subGrades){
            Double value = parse(subGrade.points().getText());
            if(value != null) chosen.put(subGrade.path(), value);
        }
        return chosen;
    }
    private static String nameOf(List<SubGrade> subGrades, String path){
        return subGrades.stream().filter(subGrade -> subGrade.path().equals(path)).map(SubGrade::name).findFirst().orElse("");
    }

    // The sub-grades of the exercise (its leaves), in the order of the grade scale
    private static List<SubGrade> getSubGrades(String exerciseName){
        ArrayList<SubGrade> list = new ArrayList<>();
        if(exerciseName == null || GradeTreeView.getTotal() == null) return list;
        for(javafx.scene.control.TreeItem<String> child : GradeTreeView.getTotal().getChildren()){
            if(!(child instanceof GradeTreeItem exercise) || !exercise.getCore().getName().equals(exerciseName)) continue;
            for(GradeTreeItem leaf : GradeTreeView.getGradesArray(exercise)){
                if(leaf.hasSubGrade() || leaf == exercise) continue;
                ArrayList<String> names = new ArrayList<>();
                for(GradeTreeItem item = leaf; item != null && item != exercise; item = item.getParent() instanceof GradeTreeItem parent ? parent : null){
                    names.addFirst(item.getCore().getName());
                }
                list.add(new SubGrade(leaf.getCore().getPath(), String.join(" › ", names), new TextField()));
            }
        }
        return list;
    }

    private static boolean isNumber(String text){
        try{
            Double.parseDouble(text.strip().replace(',', '.').replace('−', '-'));
            return true;
        }catch(NumberFormatException e){
            return false;
        }
    }

    // "1", "0,5", "-1" (the sign is given by the kind): the number of points, or null if it is not a number
    private static Double parse(String text){
        String value = text.strip().replace(',', '.').replace('−', '-');
        if(value.isEmpty()) return null;
        try{
            double points = Math.abs(Double.parseDouble(value));
            return points == 0 ? null : points;
        }catch(NumberFormatException e){
            return null;
        }
    }
}
