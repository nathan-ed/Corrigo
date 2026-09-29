/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.notes;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;

import java.io.File;
import java.text.DateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

// Side bar panel listing the teacher's notes (see TeacherNotes), newest first.
public class NotesPanel extends VBox {

    private record Palette(String background, String card, String border, String text, String muted, String accent){
        static Palette get(){
            if(StyleManager.DEFAULT_STYLE == jfxtras.styles.jmetro.Style.DARK)
                return new Palette("#1e1e1e", "#2a2a2a", "#3c3c3c", "#e8e8e8", "#9e9e9e", "#4a9eff");
            return new Palette("#f4f4f4", "#ffffff", "#d9d9d9", "#1c1c1c", "#6b6b6b", "#1565c0");
        }
    }
    private final Palette palette = Palette.get();

    private final TextField search = new TextField();
    private final CheckBox onlyThisCopy = new CheckBox(TR.tr("notes.panel.onlyThisCopy"));
    private final VBox list = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(list);

    private boolean updateScheduled;

    public NotesPanel(){
        setStyle("-fx-background-color: " + palette.background() + ";");

        Label title = new Label(TR.tr("notes.panel.title"));
        title.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: " + palette.text() + ";");
        Button newNote = new Button(TR.tr("notes.panel.new"));
        newNote.setTooltip(new Tooltip(TR.tr("notes.panel.new.tooltip")));
        newNote.setOnAction(e -> TeacherNotes.captureTextNote());
        Button screenshot = new Button(TR.tr("notes.panel.screenshot"));
        screenshot.setTooltip(new Tooltip(TR.tr("notes.panel.screenshot.tooltip")));
        screenshot.setOnAction(e -> TeacherNotes.captureScreenshotNote());
        for(Button button : new Button[]{newNote, screenshot}){
            button.setFocusTraversable(false);
            button.setStyle("-fx-padding: 4 10; -fx-background-radius: 4;");
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        HBox buttons = new HBox(6, newNote, screenshot);

        search.setPromptText(TR.tr("notes.panel.search"));
        search.textProperty().addListener((o, oldValue, newValue) -> scheduleUpdate());
        onlyThisCopy.setStyle("-fx-text-fill: " + palette.text() + ";");
        onlyThisCopy.selectedProperty().addListener((o, oldValue, newValue) -> scheduleUpdate());

        VBox header = new VBox(8, title, buttons, search, onlyThisCopy);
        header.setPadding(new Insets(10, 12, 10, 12));
        header.setStyle("-fx-border-color: " + palette.border() + "; -fx-border-width: 0 0 1 0;");

        list.setPadding(new Insets(10));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: " + palette.background() + "; -fx-background-color: " + palette.background() + ";");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, scroll);

        TeacherNotes.getNotes().addListener((ListChangeListener<TeacherNote>) c -> scheduleUpdate());
        TeacherNotes.revisionProperty().addListener((o, oldValue, newValue) -> scheduleUpdate());
        MainWindow.mainScreen.statusProperty().addListener((o, oldValue, newValue) -> {
            if(onlyThisCopy.isSelected()) scheduleUpdate();
        });
        update();
    }

    private void scheduleUpdate(){
        if(updateScheduled) return;
        updateScheduled = true;
        Platform.runLater(() -> {
            updateScheduled = false;
            update();
        });
    }

    private void update(){
        File copy = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile() : null;
        List<TeacherNote> notes = TeacherNotes.getNotes().stream()
                .filter(note -> !onlyThisCopy.isSelected() || note.isAbout(copy))
                .filter(note -> note.matches(search.getText()))
                .sorted(Comparator.comparingLong(TeacherNote::getCreated).reversed())
                .toList();

        list.getChildren().clear();
        if(notes.isEmpty()){
            Label empty = new Label(TR.tr(TeacherNotes.getNotes().isEmpty() ? "notes.panel.empty" : "notes.panel.noMatch"));
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: " + palette.muted() + ";");
            list.getChildren().add(empty);
            return;
        }
        for(TeacherNote note : notes) list.getChildren().add(new Card(note));
    }

    // "copy.pdf · p. 2 · Ex 1"
    static String formatContext(String fileName, int page, String exercise){
        StringBuilder context = new StringBuilder(fileName);
        if(page >= 0) context.append(" · ").append(TR.tr("notes.context.page", String.valueOf(page + 1)));
        if(exercise != null) context.append(" · ").append(exercise);
        return context.toString();
    }

    private class Card extends VBox {
        private final TeacherNote note;
        private final Label text = new Label();

        Card(TeacherNote note){
            super(6);
            this.note = note;
            setPadding(new Insets(8, 10, 10, 10));
            setStyle("-fx-background-color: " + palette.card() + "; -fx-border-color: " + palette.border() + "; -fx-border-radius: 6; -fx-background-radius: 6;");

            // Context (link to the copy), date, delete
            Label date = new Label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(note.getCreated())));
            date.setStyle("-fx-font-size: 10; -fx-text-fill: " + palette.muted() + ";");
            date.setMinWidth(Region.USE_PREF_SIZE);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button delete = new Button("×");
            delete.setFocusTraversable(false);
            delete.setCursor(Cursor.HAND);
            delete.setTooltip(new Tooltip(TR.tr("notes.panel.delete")));
            delete.setStyle("-fx-background-color: transparent; -fx-padding: 0 4; -fx-font-size: 14; -fx-text-fill: " + palette.muted() + ";");
            delete.setOnAction(e -> confirmDelete());
            HBox top = new HBox(6, date, spacer, delete);
            top.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(top);

            if(note.getFile() != null){
                Hyperlink context = new Hyperlink(formatContext(new File(note.getFile()).getName(), note.getPage(), note.getExercise()));
                context.setFocusTraversable(false);
                context.setWrapText(true);
                context.setPadding(Insets.EMPTY);
                context.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.accent() + ";");
                context.setTooltip(new Tooltip(TR.tr("notes.panel.goTo.tooltip")));
                context.setOnAction(e -> TeacherNotes.goTo(note));
                getChildren().add(context);
            }

            Image thumbnail = TeacherNotes.getThumbnail(note);
            if(thumbnail != null){
                ImageView image = new ImageView(thumbnail);
                image.setPreserveRatio(true);
                image.setSmooth(true);
                // The card padding and border, and the scroll bar
                image.fitWidthProperty().bind(scroll.widthProperty().subtract(50));
                image.setCursor(Cursor.HAND);
                Tooltip.install(image, new Tooltip(TR.tr("notes.panel.image.tooltip")));
                image.setOnMouseClicked(e -> {
                    if(e.getButton() == MouseButton.PRIMARY) showFullImage(note);
                });
                getChildren().add(image);
            }

            text.setWrapText(true);
            text.setCursor(Cursor.TEXT);
            text.setMaxWidth(Double.MAX_VALUE);
            showText();
            text.setOnMouseClicked(e -> edit());
            getChildren().add(text);
        }

        private void showText(){
            boolean empty = note.getText().isBlank();
            text.setText(empty ? TR.tr("notes.panel.addText") : note.getText());
            text.setStyle(empty ? "-fx-font-style: italic; -fx-text-fill: " + palette.muted() + ";" : "-fx-text-fill: " + palette.text() + ";");
        }

        // Enter or focus lost saves, Shift+Enter goes to a new line, Escape cancels.
        private void edit(){
            TextArea area = new TextArea(note.getText());
            area.setWrapText(true);
            area.setPrefRowCount(Math.max(3, note.getText().split("\n").length + 1));
            int index = getChildren().indexOf(text);
            getChildren().set(index, area);
            boolean[] done = {false};
            Runnable close = () -> {
                if(done[0]) return;
                done[0] = true;
                if(getChildren().contains(area)) getChildren().set(getChildren().indexOf(area), text);
            };
            area.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if(e.getCode() == KeyCode.ENTER){
                    e.consume();
                    if(e.isShiftDown()){
                        area.insertText(area.getCaretPosition(), "\n");
                        return;
                    }
                    TeacherNotes.setText(note, area.getText().strip());
                    showText();
                    close.run();
                }else if(e.getCode() == KeyCode.ESCAPE){
                    e.consume();
                    close.run();
                }
            });
            area.focusedProperty().addListener((o, oldValue, newValue) -> {
                if(newValue || done[0]) return;
                TeacherNotes.setText(note, area.getText().strip());
                showText();
                close.run();
            });
            Platform.runLater(() -> {
                area.requestFocus();
                area.end();
            });
        }

        private void confirmDelete(){
            CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, TR.tr("notes.panel.delete"), TR.tr("notes.panel.delete.header"));
            alert.addCancelButton(ButtonPosition.CLOSE);
            alert.addDeleteButton(ButtonPosition.DEFAULT);
            if(alert.getShowAndWaitIsDefaultButton()) TeacherNotes.delete(note);
        }
    }

    private static void showFullImage(TeacherNote note){
        Image image = TeacherNotes.getFullImage(note);
        if(image == null) return;
        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, TR.tr("notes.panel.title"),
                note.getFile() == null ? "" : formatContext(new File(note.getFile()).getName(), note.getPage(), note.getExercise()));
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(Math.min(1000, image.getWidth()));
        view.setFitHeight(Math.min(700, image.getHeight()));
        Label text = new Label(note.getText());
        text.setWrapText(true);
        text.setMaxWidth(view.getFitWidth());
        alert.getDialogPane().setContent(new VBox(10, view, text));
        alert.addOKButton(ButtonPosition.DEFAULT);
        if(note.getFile() != null){
            ButtonType goTo = alert.addButton(TR.tr("notes.panel.goTo"), ButtonPosition.OTHER_RIGHT);
            if(alert.getShowAndWait() == goTo) TeacherNotes.goTo(note);
        }else alert.show();
    }
}
