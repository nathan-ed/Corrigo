/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.ScoredCommentElement;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a changed (or deleted) catalog entry to the copies of the evaluation:
 * the open document directly, and the other open files that have the same grade scale after a preview.
 */
public class ScoredCommentPropagationDialog {

    private final ScoredComment entry;
    private final boolean delete;
    private final boolean keepAsText;

    private record Target(File file, File editFile, Config config, int linked, int withLocalChanges, double[] before, double[] after) {}

    // Pushes the current state of the entry.
    public ScoredCommentPropagationDialog(ScoredComment entry){
        this(entry, false, false);
    }
    // Removes the entry from the copies. The open document must already be handled (see ScoredComments.deleteEntry).
    public static ScoredCommentPropagationDialog forDelete(ScoredComment entry, boolean keepAsText){
        return new ScoredCommentPropagationDialog(entry, true, keepAsText);
    }
    private ScoredCommentPropagationDialog(ScoredComment entry, boolean delete, boolean keepAsText){
        this.entry = entry;
        this.delete = delete;
        this.keepAsText = keepAsText;
    }

    public void show(){
        if(!MainWindow.mainScreen.hasDocument(false)) return;

        if(!delete){
            for(ScoredCommentElement placed : ScoredComments.getPlacedComments()){
                if(entry.getId().equals(placed.getScoredCommentId())) placed.applyEntry(entry, false);
            }
        }

        List<Target> targets = prepareTargets();
        if(targets.isEmpty()) return;

        CustomAlert dialog = new CustomAlert(Alert.AlertType.CONFIRMATION, TR.tr("scoredComments.propagation.title"),
                TR.tr(delete ? "scoredComments.propagation.headerDelete" : "scoredComments.propagation.header",
                        ScoredCommentGrades.render(entry.getText(), entry.getPoints(), MainWindow.gradesDigFormat), String.valueOf(targets.size())),
                TR.tr("scoredComments.propagation.details"));

        ListView<String> list = new ListView<>();
        for(Target target : targets){
            String line = target.file().getName() + "  —  " + TR.tr("scoredComments.propagation.comments", target.linked());
            if(target.withLocalChanges() > 0) line += " " + TR.tr("scoredComments.propagation.localChanges", target.withLocalChanges());
            if(target.before() != null && target.after() != null){
                line += "  —  " + formatGrade(target.before()) + " → " + formatGrade(target.after());
            }
            list.getItems().add(line);
        }
        list.setPrefHeight(Math.min(300, 30 + targets.size() * 26));
        list.setPrefWidth(560);
        dialog.getDialogPane().setContent(list);

        dialog.addCancelButton(ButtonPosition.CLOSE);
        dialog.addButton(TR.tr("scoredComments.propagation.apply", targets.size()), ButtonPosition.DEFAULT);
        if(dialog.getShowAndWaitGetButtonPosition(ButtonPosition.CLOSE) != ButtonPosition.DEFAULT) return;

        int saved = 0;
        for(Target target : targets){
            try{
                target.config().save();
                Edition.removePreloadedEditFile(target.editFile());
                saved++;
            }catch(Exception e){
                Log.eNotified(e, "Unable to update " + target.file().getName());
            }
        }
        MainWindow.filesTab.refresh();
        MainWindow.footerBar.showToast(Color.web("#1b5e20"), Color.WHITE, TR.tr("scoredComments.propagation.done", saved));
    }

    // Loads and changes (in memory) the edit files of the other copies that use the entry.
    private List<Target> prepareTargets(){
        String signature = ScoredComments.getCurrentSignature();
        ScoredCommentCatalog catalog = ScoredComments.getCatalog();
        File current = MainWindow.mainScreen.document.getFile();

        ArrayList<Target> targets = new ArrayList<>();
        for(File file : MainWindow.filesTab.getOpenedFiles()){
            if(file.equals(current)) continue;
            File editFile = Edition.getEditFile(file);
            if(!editFile.exists()) continue;
            try{
                Config config = new Config(editFile);
                config.load();
                if(!signature.equals(ScoredCommentGrades.getSignature(config.base))) continue;

                double[] before = ScoredCommentGrades.getRootGrade(config.base);
                int linked;
                int withLocalChanges = 0;
                if(delete){
                    linked = ScoredCommentGrades.removeEntry(config.base, entry.getId(), keepAsText);
                }else{
                    ScoredCommentGrades.ApplyResult result = ScoredCommentGrades.applyEntry(config.base, entry, MainWindow.gradesDigFormat);
                    linked = result.linked();
                    withLocalChanges = result.withLocalChanges();
                }
                if(linked == 0) continue;

                ScoredCommentGrades.recomputeGrades(config.base, catalog, GradeElement::isBonus);
                targets.add(new Target(file, editFile, config, linked, withLocalChanges, before, ScoredCommentGrades.getRootGrade(config.base)));
            }catch(Exception e){
                Log.eNotified(e, "Unable to read " + editFile.getName());
            }
        }
        return targets;
    }

    private static String formatGrade(double[] grade){
        return (grade[0] < 0 ? "?" : MainWindow.gradesDigFormat.format(grade[0])) + "/" + MainWindow.gradesDigFormat.format(grade[1]);
    }
}
