/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts;

import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.OKAlert;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TextCopyToFilesDialog {

    private TextElement sourceText;

    private ConflictBehavior conflictBehavior = ConflictBehavior.ASK;

    private enum ConflictBehavior {
        ASK,
        SKIP,
        REPLACE,
        DUPLICATE
    }

    private enum ConflictDecision {
        SKIP,
        REPLACE,
        DUPLICATE,
        STOP_ALL
    }

    // 0 : Modified | 1 : Skipped | 2 : Stop All
    private static final int RESULT_MODIFIED = 0;
    private static final int RESULT_SKIPPED = 1;
    private static final int RESULT_STOP_ALL = 2;

    public void show() {
        if (!MainWindow.mainScreen.hasDocument(false)) return;
        if (!(MainWindow.mainScreen.getSelected() instanceof TextElement selected)) {
            new OKAlert(Alert.AlertType.WARNING,
                    TR.tr("textTab.copyToFilesDialog.error.noTextSelected.title"),
                    TR.tr("textTab.copyToFilesDialog.error.noTextSelected.header"),
                    null).show();
            return;
        }
        show(selected);
    }

    public void show(TextElement textElement) {
        if (!MainWindow.mainScreen.hasDocument(false) || textElement == null) return;

        sourceText = (TextElement) textElement.cloneHeadless();
        conflictBehavior = ConflictBehavior.ASK;

        CustomAlert dialog = new CustomAlert(Alert.AlertType.CONFIRMATION,
                TR.tr("textTab.copyToFilesDialog.confirmation.title"),
                TR.tr("textTab.copyToFilesDialog.confirmation.header"));

        Label content = new Label(
                TR.tr("textTab.copyToFilesDialog.confirmation.position", sourceText.getPageNumber() + 1) + "\n\n" +
                        TR.tr("textTab.copyToFilesDialog.confirmation.preview") + ":\n" +
                        getPreviewText(sourceText));
        content.setWrapText(true);
        dialog.getDialogPane().setContent(content);

        dialog.addCancelButton(ButtonPosition.CLOSE);
        dialog.addButton(TR.tr("textTab.copyToFilesDialog.confirmation.actions.copySameFolder"), ButtonPosition.DEFAULT);
        dialog.addButton(TR.tr("textTab.copyToFilesDialog.confirmation.actions.copyAll"), ButtonPosition.OTHER_RIGHT);

        ButtonPosition option = dialog.getShowAndWaitGetButtonPosition(ButtonPosition.CLOSE);
        if (option == ButtonPosition.CLOSE) return;

        List<File> openedFiles = MainWindow.filesTab.getOpenedFiles();
        boolean recursive = openedFiles.size() != 1;
        int modified = 0;
        int skipped = 0;
        boolean stopAll = false;

        for (File file : openedFiles) {
            if (MainWindow.mainScreen.document.getFile().equals(file)) continue;
            if (option == ButtonPosition.DEFAULT &&
                    !Objects.equals(MainWindow.mainScreen.document.getFile().getParent(), file.getParent())) {
                continue;
            }

            int result = copyToFile(file, recursive);
            if (result == RESULT_MODIFIED) modified++;
            else if (result == RESULT_SKIPPED) skipped++;
            else if (result == RESULT_STOP_ALL) {
                stopAll = true;
                break;
            }
        }

        if (modified == 0 && skipped == 0 && !stopAll) {
            new OKAlert(Alert.AlertType.WARNING,
                    TR.tr("textTab.copyToFilesDialog.error.noTargets.title"),
                    TR.tr("textTab.copyToFilesDialog.error.noTargets.header"),
                    null).show();
            return;
        }

        String details = "(" + TR.tr("textTab.copyToFilesDialog.completed.details", modified) + ")";
        if (skipped > 0) details += "\n(" + TR.tr("textTab.copyToFilesDialog.completed.skipped", skipped) + ")";
        if (stopAll) details += "\n(" + TR.tr("textTab.copyToFilesDialog.completed.stopped") + ")";

        new OKAlert(TR.tr("textTab.copyToFilesDialog.completed.title"),
                TR.tr("textTab.copyToFilesDialog.completed.header"),
                details).show();

        MainWindow.filesTab.refresh();
    }

    public int copyToFile(File file, boolean recursive) {
        if (sourceText == null) return RESULT_SKIPPED;
        try {
            File editFile = Edition.getEditFile(file);
            ArrayList<Element> elements = new ArrayList<>(Arrays.asList(Edition.simpleLoad(editFile)));

            ArrayList<TextElement> collidingTexts = elements.stream()
                    .filter(TextElement.class::isInstance)
                    .map(TextElement.class::cast)
                    .filter(this::isSamePosition)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            if (!collidingTexts.isEmpty()) {
                if (collidingTexts.stream().anyMatch(this::isSameTextElement)) {
                    return RESULT_SKIPPED;
                }

                ConflictBehavior behavior = conflictBehavior;
                if (behavior == ConflictBehavior.ASK) {
                    ConflictDecision decision = askConflictDecision(file, collidingTexts.size(), recursive);
                    if (decision == ConflictDecision.STOP_ALL) return RESULT_STOP_ALL;
                    if (decision == ConflictDecision.SKIP) return RESULT_SKIPPED;
                    behavior = decision == ConflictDecision.REPLACE ? ConflictBehavior.REPLACE : ConflictBehavior.DUPLICATE;
                }

                if (behavior == ConflictBehavior.SKIP) {
                    return RESULT_SKIPPED;
                } else if (behavior == ConflictBehavior.REPLACE) {
                    elements.removeIf(element -> element instanceof TextElement text && isSamePosition(text));
                }
            }

            elements.add(copySource());
            Edition.simpleSave(editFile, elements.toArray(new Element[0]));
            return RESULT_MODIFIED;
        } catch (Exception e) {
            Log.eNotified(e);
            return RESULT_SKIPPED;
        }
    }

    private ConflictDecision askConflictDecision(File file, int collisions, boolean recursive) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.WARNING,
                TR.tr("textTab.copyToFilesDialog.error.conflict.title"),
                TR.tr("textTab.copyToFilesDialog.error.conflict.header", file.getName(),
                        String.valueOf(collisions), String.valueOf(sourceText.getPageNumber() + 1)),
                TR.tr("textTab.copyToFilesDialog.error.conflict.details"));

        ButtonType skip = alert.getButton(TR.tr("dialog.actionError.skip"), ButtonPosition.CLOSE);
        ButtonType skipAll = alert.getButton(TR.tr("textTab.copyToFilesDialog.error.conflict.actions.skipAlways"), ButtonPosition.CLOSE);
        ButtonType replace = alert.getButton(TR.tr("dialog.actionError.overwrite"), ButtonPosition.DEFAULT);
        ButtonType replaceAll = alert.getButton(TR.tr("dialog.actionError.overwriteAlways"), ButtonPosition.OTHER_RIGHT);
        ButtonType duplicate = alert.getButton(TR.tr("textTab.copyToFilesDialog.error.conflict.actions.duplicate"), ButtonPosition.DEFAULT);
        ButtonType duplicateAll = alert.getButton(TR.tr("textTab.copyToFilesDialog.error.conflict.actions.duplicateAlways"), ButtonPosition.OTHER_RIGHT);
        ButtonType stopAll = alert.getButton(TR.tr("dialog.actionError.stopAll"), ButtonPosition.CLOSE);

        if (recursive) {
            alert.getButtonTypes().setAll(replace, replaceAll, duplicate, duplicateAll, skip, skipAll, stopAll);
        } else {
            alert.getButtonTypes().setAll(replace, duplicate, skip);
        }

        ButtonType option = alert.getShowAndWait();
        if (option == null || option == skip) return ConflictDecision.SKIP;
        if (option == stopAll) return ConflictDecision.STOP_ALL;
        if (option == skipAll) {
            conflictBehavior = ConflictBehavior.SKIP;
            return ConflictDecision.SKIP;
        }
        if (option == replaceAll) {
            conflictBehavior = ConflictBehavior.REPLACE;
            return ConflictDecision.REPLACE;
        }
        if (option == duplicateAll) {
            conflictBehavior = ConflictBehavior.DUPLICATE;
            return ConflictDecision.DUPLICATE;
        }
        if (option == replace) {
            conflictBehavior = ConflictBehavior.ASK;
            return ConflictDecision.REPLACE;
        }
        if (option == duplicate) {
            conflictBehavior = ConflictBehavior.ASK;
            return ConflictDecision.DUPLICATE;
        }
        return ConflictDecision.SKIP;
    }

    private boolean isSamePosition(TextElement other) {
        return other.getPageNumber() == sourceText.getPageNumber()
                && other.getRealX() == sourceText.getRealX()
                && other.getRealY() == sourceText.getRealY();
    }

    private boolean isSameTextElement(TextElement other) {
        return isSamePosition(other)
                && Objects.equals(other.getText(), sourceText.getText())
                && Objects.equals(other.getColor(), sourceText.getColor())
                && Double.compare(other.getTextMaxWidth(), sourceText.getTextMaxWidth()) == 0
                && Double.compare(other.getFont().getSize(), sourceText.getFont().getSize()) == 0
                && Objects.equals(other.getFont().getFamily(), sourceText.getFont().getFamily())
                && FontUtils.getFontPosture(other.getFont()) == FontUtils.getFontPosture(sourceText.getFont())
                && FontUtils.getFontWeight(other.getFont()) == FontUtils.getFontWeight(sourceText.getFont());
    }

    private TextElement copySource() {
        return (TextElement) sourceText.cloneHeadless();
    }

    private String getPreviewText(TextElement textElement) {
        String text = textElement.getText();
        if (text == null || text.isBlank()) return TR.tr("textTab.copyToFilesDialog.confirmation.preview.empty");
        text = text.replace("\r", "").replace('\n', ' ');
        if (text.length() > 120) return text.substring(0, 117) + "...";
        return text;
    }
}
