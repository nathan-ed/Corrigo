/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import corrigo.datasaving.evaluation.EvaluationFolders;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import corrigo.panel.sidebar.grades.tags.ExerciseTags;
import corrigo.panel.sidebar.texts.evaluation.EvaluationComments;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ConfirmAlert;
import javafx.application.Platform;
import javafx.scene.paint.Color;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tools menu: grading of the evaluation started again on all its copies (the copies stay).
 * Restart: comments, marks, methods and mistakes placed and grades are erased, the grade scale stays.
 * Reset the grade scale: the same, and the grade scale is removed too.
 * The edit files and the tags of the evaluation are copied first into .pdf4teachers/backups/<date>/.
 */
public final class GradingReset {

    static final String BACKUPS_DIR = "backups";
    // Keys of a grade that belong to the grading of the copy, not to the grade scale
    private static final List<String> GRADING_KEYS = List.of(ScoredCommentGrades.KEY_VALUE_SOURCE, ScoredCommentGrades.KEY_VALUE_BEFORE,
            ScoredCommentGrades.KEY_START, ScoredCommentGrades.KEY_MODE, ScoredCommentGrades.KEY_AUTO_MODE);

    private GradingReset(){
    }

    // PURE

    // The edit file of a copy without its annotations and grades; without its grade scale too if keepScale is false.
    // Returns false if nothing is left in it (the edit file can be deleted).
    static boolean clear(Map<String, Object> editBase, boolean keepScale){
        editBase.put("texts", new LinkedHashMap<String, Object>());
        editBase.put("images", new LinkedHashMap<String, Object>());
        editBase.put("vectors", new LinkedHashMap<String, Object>());
        editBase.remove("lastScrollValue");
        if(keepScale){
            for(Map<String, Object> grade : ScoredCommentGrades.getGrades(editBase)){
                grade.put("value", -1d);
                GRADING_KEYS.forEach(grade::remove);
            }
        }else editBase.put("grades", new ArrayList<>());

        boolean hasGrades = !ScoredCommentGrades.getGrades(editBase).isEmpty();
        boolean hasSkills = editBase.get("skills") instanceof Map<?, ?> skills && !skills.isEmpty();
        return hasGrades || hasSkills;
    }

    // ACTIONS

    public static void restartGrading(){
        run(true);
    }
    public static void resetGradeScale(){
        run(false);
    }

    private static void run(boolean keepScale){
        if(!MainWindow.mainScreen.hasDocument(true)) return;
        File open = MainWindow.mainScreen.document.getFile();
        File folder = EvaluationFolders.getActiveFolder();
        List<File> copies = ExerciseTags.getFolderCopies();
        if(folder == null || copies.isEmpty()) return;

        String kind = keepScale ? "restart" : "resetScale";
        ConfirmAlert alert = new ConfirmAlert(false, TR.tr("gradingReset." + kind + ".confirm.header", String.valueOf(copies.size())));
        alert.setContentText(TR.tr("gradingReset." + kind + ".confirm.details") + "\n\n" + TR.tr("gradingReset.confirm.backup"));
        if(!alert.execute()) return;

        // The open copy is saved and closed: its edit file is cleared like the others, then it is opened again
        if(!MainWindow.mainScreen.closeFile(false, false, false)) return;
        EvaluationFolders.saveAllNow();

        File backup;
        try{
            backup = backup(folder, copies);
        }catch(IOException e){
            Log.eNotified(e, "Unable to back up the grading of " + folder);
            MainWindow.mainScreen.openFile(open);
            return;
        }

        for(File copy : copies){
            File editFile = Edition.getEditFile(copy);
            if(!editFile.exists()) continue;
            try{
                Config config = new Config(editFile);
                config.load();
                if(clear(config.base, keepScale)) config.save();
                else Files.delete(editFile.toPath());
            }catch(Exception e){
                Log.eNotified(e, "Unable to clear the grading of " + copy.getName());
            }
            Edition.removePreloadedEditFile(editFile);
        }

        // The panels are told once the copy is open again (no exercise to show while it is closed)
        ExerciseTags.getData().clearUses();
        EvaluationFolders.requestSave(ExerciseTags.FOLDER_PART);
        EvaluationFolders.saveAllNow();
        EvaluationComments.rescanAll();
        MainWindow.filesTab.refresh();

        MainWindow.mainScreen.openFile(open, true);
        Platform.runLater(() -> {
            ExerciseTags.fireChanged(false);
            MainWindow.footerBar.showToast(Color.web("#1b5e20"), Color.WHITE, FooterBar.ToastDuration.LONG,
                    TR.tr("gradingReset." + kind + ".done", String.valueOf(copies.size()), backup.getName()));
        });
    }

    // Copies the edit files of the copies and the tags of the evaluation into a new folder of .pdf4teachers/backups/.
    private static File backup(File folder, List<File> copies) throws IOException{
        File backup = new File(new File(EvaluationFolders.getDataDir(folder), BACKUPS_DIR),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
        Files.createDirectories(backup.toPath());
        for(File copy : copies){
            File editFile = Edition.getEditFile(copy);
            if(editFile.exists()) Files.copy(editFile.toPath(), new File(backup, editFile.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        File tags = EvaluationFolders.getFile(folder, ExerciseTags.FOLDER_PART);
        if(tags.exists()) Files.copy(tags.toPath(), new File(backup, tags.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }
}
