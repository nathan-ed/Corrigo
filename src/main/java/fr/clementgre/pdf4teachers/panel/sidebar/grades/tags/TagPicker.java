/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Popup opened with # at the mouse: search the methods and mistakes (those of the exercise being graded first, then
 * the others), or type a new one.
 * Enter adds it to the copy (where the mouse is) or removes it; Tab switches between method and mistake for a new one.
 */
public final class TagPicker {

    public static final String METHOD_COLOR = "#1565c0", MISTAKE_COLOR = "#c62828";
    private static final String METHOD_COLOR_DARK = "#4a9eff", MISTAKE_COLOR_DARK = "#ef5350";
    private static Popup open;

    private TagPicker(){
    }

    public static boolean isDark(){
        return StyleManager.DEFAULT_STYLE == jfxtras.styles.jmetro.Style.DARK;
    }
    public static String getColor(Kind kind){
        if(kind == Kind.METHOD) return isDark() ? METHOD_COLOR_DARK : METHOD_COLOR;
        return isDark() ? MISTAKE_COLOR_DARK : MISTAKE_COLOR;
    }

    // A new tag to create with the typed name
    private record Create(String name) {}

    public static void open(){
        GradeTreeItem exercise = ExerciseTags.getExercise();
        String copy = ExerciseTags.getOpenCopy();
        if(exercise == null || copy == null){
            MainWindow.footerBar.showToast(Color.web("#6a1b1b"), Color.WHITE, FooterBar.ToastDuration.MEDIUM, TR.tr("tags.picker.noExercise"));
            return;
        }
        if(open != null) open.hide();
        String exerciseName = exercise.getCore().getName();
        Placement placement = ExerciseTags.getMousePlacement(); // Where the mouse is now
        EvaluationTags data = ExerciseTags.getData();
        boolean dark = isDark();
        String background = dark ? "#2a2a2a" : "#ffffff", text = dark ? "#e8e8e8" : "#1c1c1c", muted = dark ? "#9e9e9e" : "#6b6b6b", border = dark ? "#3c3c3c" : "#d0d0d0";

        Label title = new Label(exerciseName + "  ·  " + TR.tr("tags.card.title"));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: " + text + ";");

        ToggleGroup kindGroup = new ToggleGroup();
        ToggleButton method = new ToggleButton(TR.tr("tags.kind.method"));
        ToggleButton mistake = new ToggleButton(TR.tr("tags.kind.mistake"));
        for(ToggleButton button : new ToggleButton[]{method, mistake}){
            button.setToggleGroup(kindGroup);
            button.setFocusTraversable(false);
        }
        Runnable styleKinds = () -> {
            for(ToggleButton button : new ToggleButton[]{method, mistake}){
                String color = getColor(button == method ? Kind.METHOD : Kind.MISTAKE);
                button.setStyle(button.isSelected()
                        ? "-fx-background-color: " + color + "; -fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 2 10;"
                        : "-fx-background-color: transparent; -fx-text-fill: " + color + "; -fx-border-color: " + color + "; -fx-border-radius: 12; -fx-padding: 2 10;");
            }
        };
        kindGroup.selectedToggleProperty().addListener((o, oldValue, newValue) -> {
            if(newValue == null) kindGroup.selectToggle(oldValue);
            styleKinds.run();
        });
        method.setSelected(true);
        Label kindLabel = new Label(TR.tr("tags.picker.newAs"));
        kindLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + muted + ";");
        HBox kinds = new HBox(6, kindLabel, method, mistake);
        kinds.setAlignment(Pos.CENTER_LEFT);

        TextField field = new TextField();
        field.setPromptText(TR.tr("tags.picker.prompt"));

        ListView<Object> list = new ListView<>();
        list.setFocusTraversable(false);
        list.setFixedCellSize(30);
        list.setCellFactory(view -> new ListCell<>() {
            { // The selected row is on the accent color: the colored texts become white
                selectedProperty().addListener((o, oldValue, newValue) -> updateItem(getItem(), isEmpty()));
            }
            @Override protected void updateItem(Object item, boolean empty){
                super.updateItem(item, empty);
                setText(null);
                if(empty || item == null){
                    setGraphic(null);
                    return;
                }
                if(item instanceof Create create){
                    Kind kind = method.isSelected() ? Kind.METHOD : Kind.MISTAKE;
                    Label label = new Label(TR.tr(kind == Kind.METHOD ? "tags.picker.createMethod" : "tags.picker.createMistake", create.name()));
                    label.setStyle("-fx-text-fill: " + (isSelected() ? "white" : getColor(kind)) + "; -fx-font-style: italic;");
                    setGraphic(label);
                    return;
                }
                Tag tag = (Tag) item;
                Circle dot = new Circle(5, Color.web(getColor(tag.getKind())));
                dot.setStroke(isSelected() ? Color.WHITE : Color.TRANSPARENT);
                Label name = new Label(tag.getName());
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                int copies = data.getCopies(exerciseName, tag).size(); // In this exercise
                Label count = new Label(copies == 0 ? "" : String.valueOf(copies));
                count.setStyle("-fx-font-size: 11; -fx-opacity: .7;");
                Label check = new Label(data.has(copy, exerciseName, tag) ? "✓" : "");
                check.setMinWidth(14);
                check.setStyle("-fx-font-weight: bold;");
                HBox row = new HBox(8, dot, name, spacer, count, check);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        Runnable filter = () -> {
            String query = field.getText().strip().toLowerCase(Locale.ROOT);
            ArrayList<Object> items = new ArrayList<>();
            for(Tag tag : data.getTags(exerciseName)){
                if(query.isEmpty() || tag.getName().toLowerCase(Locale.ROOT).contains(query)) items.add(tag);
            }
            if(!query.isEmpty() && data.find(field.getText()).isEmpty()) items.add(new Create(field.getText().strip()));
            list.getItems().setAll(items);
            list.getSelectionModel().selectFirst();
            // As high as its rows, up to 7 rows
            list.setPrefHeight(Math.clamp(items.size() * 30 + 4, 34, 7 * 30 + 4));
        };
        field.textProperty().addListener((o, oldValue, newValue) -> filter.run());
        kindGroup.selectedToggleProperty().addListener((o, oldValue, newValue) -> list.refresh());
        filter.run();

        Label hint = new Label(TR.tr("tags.picker.hint"));
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 11; -fx-text-fill: " + muted + ";");

        VBox box = new VBox(8, title, field, list, kinds, hint);
        box.setPadding(new Insets(12));
        box.setPrefWidth(340);
        box.setStyle("-fx-background-color: " + background + "; -fx-border-color: " + border + "; -fx-border-radius: 8; -fx-background-radius: 8;");
        box.setEffect(new DropShadow(16, Color.rgb(0, 0, 0, .3)));
        StyleManager.putStyle(box, fr.clementgre.pdf4teachers.utils.style.Style.DEFAULT);

        Popup popup = new Popup();
        popup.getContent().add(box);
        popup.setAutoHide(true);
        popup.setOnHidden(e -> {
            if(open == popup) open = null;
        });
        open = popup;

        Runnable apply = () -> {
            Object item = list.getSelectionModel().getSelectedItem();
            Tag tag;
            if(item instanceof Tag existing) tag = existing;
            else if(item instanceof Create create) tag = data.create(exerciseName, create.name(), method.isSelected() ? Kind.METHOD : Kind.MISTAKE);
            else return;
            popup.hide();
            // Already on the copy, pointed elsewhere: moved here; pointed at it (or not on a page): removed
            Placement current = data.getPlacement(copy, exerciseName, tag);
            if(current != null && !ExerciseTags.isExerciseSpot(placement) && !ExerciseTags.isNear(current, placement)){
                data.add(copy, exerciseName, tag, placement);
                ExerciseTags.fireChanged(true);
                MainWindow.footerBar.showToast(Color.web(getColor(tag.getKind())), Color.WHITE, TR.tr("tags.picker.moved", tag.getName()));
                return;
            }
            boolean has = ExerciseTags.toggleOnOpenCopy(exerciseName, tag, placement);
            MainWindow.footerBar.showToast(Color.web(getColor(tag.getKind())), Color.WHITE,
                    TR.tr(has ? "tags.picker.added" : "tags.picker.removed", tag.getName()));
        };
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch(e.getCode()){
                case ENTER -> {
                    e.consume();
                    apply.run();
                }
                case ESCAPE -> {
                    e.consume();
                    popup.hide();
                }
                case TAB -> {
                    e.consume();
                    kindGroup.selectToggle(method.isSelected() ? mistake : method);
                }
                case DOWN, UP -> {
                    e.consume();
                    int index = list.getSelectionModel().getSelectedIndex() + (e.getCode() == KeyCode.DOWN ? 1 : -1);
                    if(index >= 0 && index < list.getItems().size()){
                        list.getSelectionModel().select(index);
                        list.scrollTo(index);
                    }
                }
                default -> {
                }
            }
        });
        list.setOnMouseClicked(e -> {
            if(e.getClickCount() == 1 && list.getSelectionModel().getSelectedItem() != null) apply.run();
        });

        Point2D mouse = new Robot().getMousePosition();
        popup.show(MainWindow.mainScreen.getScene().getWindow(), mouse.getX() + 8, mouse.getY() + 8);
        Platform.runLater(field::requestFocus);
    }
}
