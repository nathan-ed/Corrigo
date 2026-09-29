/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;

// Creates or edits a catalog entry.
public class ScoredCommentEditDialog {

    private static final double[] PRESETS = {-2, -1, -0.5, -0.25, 0.25, 0.5, 1};

    private ScoredCommentEditDialog(){
    }

    // Paths of the sub-grades that can receive comments (grades with no sub-grades, root excepted).
    public static List<String> getLeafGradePaths(){
        if(GradeTreeView.getTotal() == null) return List.of();
        return GradeTreeView.getGradesArray(GradeTreeView.getTotal()).stream()
                .filter(item -> !item.hasSubGrade() && !item.isRoot())
                .map(GradeTreeItem::getCore)
                .map(grade -> grade.getPath())
                .toList();
    }
    // "Ex 1 › a" for "\Total\Ex 1\a"
    public static String getGradeDisplayName(String gradePath){
        String[] parts = gradePath.split("\\\\");
        StringBuilder name = new StringBuilder();
        // parts[0] is empty and parts[1] is the root
        for(int i = 2; i < parts.length; i++){
            if(!name.isEmpty()) name.append(" › ");
            name.append(parts[i]);
        }
        return name.isEmpty() ? gradePath : name.toString();
    }

    /**
     * @param entry The entry to edit, or null to create one.
     * @param defaultGradePath The sub-grade selected by default when creating an entry.
     * @return The edited entry (the given one) or the new entry. Empty if canceled.
     */
    public static Optional<ScoredComment> show(ScoredComment entry, String defaultGradePath){
        if(entry == null) return show(new ScoredComment(defaultGradePath, "", -.5, null), true);
        return show(entry, false);
    }
    /**
     * @param entry The entry to edit, or the prefilled entry to create (not in the catalog yet).
     * @return The edited entry. Empty if canceled.
     */
    public static Optional<ScoredComment> show(ScoredComment entry, boolean creating){
        List<String> gradePaths = getLeafGradePaths();
        if(gradePaths.isEmpty()){
            MainWindow.footerBar.showToast(Color.web("#6a1b1b"), Color.WHITE, TR.tr("scoredComments.error.noGrades"));
            return Optional.empty();
        }

        CustomAlert dialog = new CustomAlert(Alert.AlertType.CONFIRMATION,
                TR.tr(creating ? "scoredComments.editDialog.newTitle" : "scoredComments.editDialog.editTitle"),
                TR.tr("scoredComments.editDialog.header"));

        ComboBox<String> grade = new ComboBox<>();
        grade.getItems().setAll(gradePaths);
        grade.setConverter(new StringConverter<>() {
            @Override public String toString(String path){
                return path == null ? "" : getGradeDisplayName(path);
            }
            @Override public String fromString(String string){
                return null;
            }
        });
        String selectedPath = entry.getGradePath();
        if(gradePaths.contains(selectedPath)) grade.getSelectionModel().select(selectedPath);
        else grade.getSelectionModel().selectFirst();
        grade.setMaxWidth(Double.MAX_VALUE);

        TextField comment = new TextField(entry.getText());
        comment.setPromptText(TR.tr("scoredComments.editDialog.commentPrompt"));
        comment.setPrefColumnCount(28);

        Spinner<Double> points = new Spinner<>(-1000, 1000, entry.getPoints(), .25);
        points.setEditable(true);
        points.setPrefWidth(100);
        HBox presets = new HBox(4);
        for(double preset : PRESETS){
            Button button = new Button(ScoredCommentGrades.formatPoints(preset, MainWindow.gradesDigFormat));
            button.setOnAction(e -> points.getValueFactory().setValue(preset));
            button.setFocusTraversable(false);
            presets.getChildren().add(button);
        }
        HBox pointsBox = new HBox(10, points, presets);

        CheckBox autoColor = new CheckBox(TR.tr("scoredComments.editDialog.autoColor"));
        autoColor.setSelected(entry.getColor() == null);
        ColorPicker color = new ColorPicker(entry.getColor() == null ? ScoredComments.NEGATIVE_COLOR : Color.web(entry.getColor()));
        color.disableProperty().bind(autoColor.selectedProperty());
        HBox colorBox = new HBox(10, autoColor, color);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15, 0, 5, 0));
        grid.addRow(0, new Label(TR.tr("scoredComments.editDialog.grade")), grade);
        grid.addRow(1, new Label(TR.tr("scoredComments.editDialog.comment")), comment);
        grid.addRow(2, new Label(TR.tr("scoredComments.points")), pointsBox);
        grid.addRow(3, new Label(TR.tr("scoredComments.editDialog.color")), colorBox);
        GridPane.setHgrow(grade, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);

        dialog.addCancelButton(ButtonPosition.CLOSE);
        dialog.addButton(TR.tr(creating ? "scoredComments.editDialog.create" : "actions.save"), ButtonPosition.DEFAULT);
        Platform.runLater(comment::requestFocus);

        if(dialog.getShowAndWaitGetButtonPosition(ButtonPosition.CLOSE) != ButtonPosition.DEFAULT) return Optional.empty();

        entry.setGradePath(grade.getSelectionModel().getSelectedItem());
        entry.setText(comment.getText().trim());
        entry.setPoints(readSpinner(points));
        entry.setColor(autoColor.isSelected() ? null : toHex(color.getValue()));
        return Optional.of(entry);
    }

    // The typed value is only committed by the spinner on focus lost.
    private static double readSpinner(Spinner<Double> spinner){
        String text = spinner.getEditor().getText().trim();
        Optional<ScoredCommentGrades.Parsed> parsed = ScoredCommentGrades.parse(text);
        if(parsed.isPresent()) return parsed.get().points();
        try{
            return Double.parseDouble(text.replace(",", ".").replace(ScoredCommentGrades.MINUS, "-"));
        }catch(NumberFormatException e){
            return spinner.getValue();
        }
    }
    static String toHex(Color color){
        return String.format("#%02x%02x%02x", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
    }
}
