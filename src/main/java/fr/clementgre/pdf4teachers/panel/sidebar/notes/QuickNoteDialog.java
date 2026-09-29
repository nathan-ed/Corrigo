/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.notes;

import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

// Asks the text of a new note, showing the screenshot if there is one. Enter saves, Shift+Enter goes to a new line.
final class QuickNoteDialog {

    private QuickNoteDialog(){
    }

    static void show(Image image, PageRenderer page){
        CustomAlert dialog = new CustomAlert(Alert.AlertType.CONFIRMATION, TR.tr("notes.dialog.title"), TR.tr(image == null ? "notes.dialog.header" : "notes.dialog.header.screenshot"));

        VBox content = new VBox(8);
        String context = getContext(page);
        if(context != null){
            Label contextLabel = new Label(context);
            contextLabel.setStyle("-fx-opacity: .7;");
            content.getChildren().add(contextLabel);
        }
        if(image != null){
            ImageView preview = new ImageView(image);
            preview.setPreserveRatio(true);
            preview.setFitWidth(Math.min(560, image.getWidth()));
            preview.setFitHeight(Math.min(360, image.getHeight()));
            content.getChildren().add(preview);
        }
        TextArea text = new TextArea();
        text.setWrapText(true);
        text.setPrefRowCount(4);
        text.setPrefColumnCount(40);
        text.setPromptText(TR.tr("notes.dialog.prompt"));
        content.getChildren().add(text);
        dialog.getDialogPane().setContent(content);

        dialog.addCancelButton(ButtonPosition.CLOSE);
        ButtonType save = dialog.addButton(TR.tr("actions.save"), ButtonPosition.DEFAULT);
        text.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if(e.getCode() != KeyCode.ENTER) return;
            e.consume();
            if(e.isShiftDown()) text.insertText(text.getCaretPosition(), "\n");
            else ((Button) dialog.getDialogPane().lookupButton(save)).fire();
        });
        Platform.runLater(text::requestFocus);

        if(dialog.getShowAndWaitGetButtonPosition(ButtonPosition.CLOSE) != ButtonPosition.DEFAULT) return;
        String value = text.getText().strip();
        if(value.isEmpty() && image == null) return;
        TeacherNotes.create(value, image, page);
    }

    // "copy.pdf · page 2 · Ex 1", or null if no document is open.
    private static String getContext(PageRenderer page){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        String exercise = MainWindow.footerBar.getExerciseCount() > 0 ? MainWindow.footerBar.getSelectedExerciseKey() : null;
        return NotesPanel.formatContext(MainWindow.mainScreen.document.getFile().getName(), page == null ? -1 : page.getPage(), exercise);
    }
}
