/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import corrigo.panel.sidebar.texts.evaluation.CommentBank;
import corrigo.panel.sidebar.texts.evaluation.EvaluationComments;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Comments of the evaluation suggested under a comment field of the grading panel: those written in this field (for this
 * grade) first, then those of the exercise, then the others (CommentBank.suggest).
 * Shown when the empty field gets the focus and while typing; Down/Up select one, Enter or a click writes it.
 */
public class CommentSuggestions {

    private static final int MAX = 30, VISIBLE_ROWS = 6, ROW_HEIGHT = 26;

    // grade: path of the grade and name of its exercise (null: none)
    public record Grade(String path, String exercise) {}

    private final TextField field;
    private final Supplier<Grade> grade;
    private final Consumer<String> onChosen;
    private final Popup popup = new Popup();
    private final ListView<CommentBank.Entry> list = new ListView<>();
    private final Label title = new Label();
    private boolean choosing; // The text is being set by a choice

    public CommentSuggestions(TextField field, Supplier<Grade> grade, Consumer<String> onChosen, String background, String border, String text, String muted){
        this.field = field;
        this.grade = grade;
        this.onChosen = onChosen;

        title.setStyle("-fx-font-size: 11; -fx-text-fill: " + muted + ";");
        list.setFocusTraversable(false);
        list.setFixedCellSize(ROW_HEIGHT);
        list.setCellFactory(view -> new ListCell<>() {
            // Built once: only the texts change when the list is filtered (new nodes would be styled at each key)
            private final Label label = new Label();
            private final Label detail = new Label();
            private final HBox row = new HBox(8, label, detail);
            { // As wide as the list: long comments are cut, never scrolled horizontally
                setPrefWidth(0);
                label.setMinWidth(0);
                label.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(label, Priority.ALWAYS);
                detail.setMinWidth(Region.USE_PREF_SIZE);
                detail.setStyle("-fx-font-size: 11; -fx-opacity: .65;");
                row.setAlignment(Pos.CENTER_LEFT);
                // Chooses on press: the field would lose the focus on release
                setOnMousePressed(e -> {
                    if(e.getButton() == MouseButton.PRIMARY && getItem() != null){
                        e.consume();
                        choose(getItem());
                    }
                });
            }
            @Override protected void updateItem(CommentBank.Entry entry, boolean empty){
                super.updateItem(entry, empty);
                setText(null);
                if(empty || entry == null){
                    setGraphic(null);
                    return;
                }
                label.setText(entry.getText().replace('\n', ' '));
                // Where it comes from, if not from this field
                Grade current = grade.get();
                int rank = current == null ? 2 : CommentBank.getRank(entry, current.path(), current.exercise());
                String origin = rank == 0 ? "" : rank == 1 ? TR.tr("gradingPanel.suggestions.exercise")
                        : entry.getExercise() == null ? TR.tr("gradingPanel.suggestions.general") : entry.getExercise();
                detail.setText(origin + (entry.getUses() > 1 ? (origin.isEmpty() ? "" : " · ") + "×" + entry.getUses() : ""));
                if(getGraphic() != row) setGraphic(row);
            }
        });

        VBox box = new VBox(4, title, list);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-background-color: " + background + "; -fx-border-color: " + border + "; -fx-border-radius: 6; -fx-background-radius: 6;");
        // No shadow: its transparent margin shows as a frame on desktops without compositing
        StyleManager.putStyle(box, fr.clementgre.pdf4teachers.utils.style.Style.DEFAULT);
        popup.getContent().add(box);
        popup.setAutoFix(true);

        field.focusedProperty().addListener((o, oldValue, newValue) -> {
            scanned = false;
            if(newValue){
                if(field.getText().isBlank()) Platform.runLater(this::update); // Once the panel scrolled to the field
            }else hide();
        });
        field.textProperty().addListener((o, oldValue, newValue) -> {
            if(field.isFocused() && !choosing) update();
        });
        // The field moves: the list would stay where it was
        field.localToSceneTransformProperty().addListener((o, oldValue, newValue) -> {
            if(popup.isShowing()) place();
        });
    }

    private boolean scanned;

    public boolean isShowing(){
        return popup.isShowing();
    }
    public void hide(){
        popup.hide();
        list.getSelectionModel().clearSelection();
    }

    /**
     * Keys of the field while the suggestions are shown. Returns true if the key was used (and consumed).
     * Down/Up move in the list, Enter writes the selected one, Escape closes the list if one is selected.
     */
    public boolean onKey(KeyEvent e){
        if(!popup.isShowing() || e.isShortcutDown() || e.isAltDown()) return false;
        int index = list.getSelectionModel().getSelectedIndex();
        switch(e.getCode()){
            case DOWN -> {
                select(Math.min(index + 1, list.getItems().size() - 1));
                e.consume();
                return true;
            }
            case UP -> {
                if(index < 0) return false;
                if(index == 0) list.getSelectionModel().clearSelection();
                else select(index - 1);
                e.consume();
                return true;
            }
            case ENTER -> {
                if(index < 0 || e.isShiftDown()) return false;
                e.consume();
                choose(list.getSelectionModel().getSelectedItem());
                return true;
            }
            case ESCAPE -> {
                if(index < 0) return false;
                e.consume();
                hide();
                return true;
            }
            case TAB -> hide();
            default -> {
            }
        }
        return false;
    }

    private void select(int index){
        if(index < 0) return;
        list.getSelectionModel().select(index);
        list.scrollTo(Math.max(0, index - VISIBLE_ROWS + 1));
    }

    private void choose(CommentBank.Entry entry){
        if(entry == null) return;
        hide();
        choosing = true;
        try{
            field.setText(entry.getText());
        }finally{
            choosing = false;
        }
        field.end();
        onChosen.accept(entry.getText());
    }

    private void update(){
        Grade current = grade.get();
        if(!field.isFocused() || current == null || field.getScene() == null){
            hide();
            return;
        }
        // The comments just written must be known: once when the field gets the focus, not at each key (the scan of the
        // copy is too slow to be done while typing; the comment being typed is left out below anyway)
        if(!scanned){
            scanned = true;
            EvaluationComments.scanPendingNow();
        }
        // Not the comment of this field on this copy (being typed): only on this copy, for this grade
        String copy = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile().getName() : null;
        List<CommentBank.Entry> entries = EvaluationComments.getBank().suggest(current.path(), current.exercise(), field.getText(), MAX + 1).stream()
                .filter(entry -> !(entry.getUses() == 1 && current.path().equals(entry.getField(copy))))
                .limit(MAX).toList();
        if(entries.isEmpty()){
            hide();
            return;
        }
        if(!entries.equals(list.getItems())){ // Unchanged while typing a word that filters nothing more
            CommentBank.Entry selected = list.getSelectionModel().getSelectedItem();
            list.getItems().setAll(entries);
            if(selected != null && entries.contains(selected)) list.getSelectionModel().select(selected);
            else list.getSelectionModel().clearSelection();
        }
        list.setPrefHeight(Math.min(entries.size(), VISIBLE_ROWS) * ROW_HEIGHT + 2);
        title.setText(TR.tr(field.getText().isBlank() ? "gradingPanel.suggestions.title" : "gradingPanel.suggestions.matching"));
        place();
    }

    // Under the field, as wide as it
    private void place(){
        Bounds bounds = field.localToScreen(field.getBoundsInLocal());
        if(bounds == null || !field.isFocused()){
            hide();
            return;
        }
        list.setPrefWidth(Math.max(bounds.getWidth() - 12, 380));
        if(popup.isShowing()){
            popup.setX(bounds.getMinX());
            popup.setY(bounds.getMaxY() + 2);
        }else popup.show(field, bounds.getMinX(), bounds.getMaxY() + 2);
    }
}
