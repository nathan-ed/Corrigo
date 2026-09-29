/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.datasaving.evaluation.EvaluationFolders;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.ScoredCommentElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.File;
import java.util.*;

/**
 * The comments of the open evaluation, by exercise (see CommentBank), stored in its folder ("comments.yml").
 * The comments of all the copies of the folder are read when its first copy is opened, and those of the open copy are followed while it is edited.
 */
public final class EvaluationComments {

    private static CommentBank bank = new CommentBank();
    private static final SimpleLongProperty revision = new SimpleLongProperty();
    // The copies of the active folder have been read
    private static boolean folderScanned;
    private static final PauseTransition openCopyDelay = new PauseTransition(Duration.millis(700));
    static {
        openCopyDelay.setOnFinished(e -> scanOpenCopy());
    }

    public static final EvaluationFolders.Part FOLDER_PART = new EvaluationFolders.Part() {
        @Override public String getFileName(){
            return "comments";
        }
        @Override public void load(File folder, Config config){
            bank = CommentBank.fromYAML(config.getList("comments"));
            folderScanned = false;
            fireChanged(false);
        }
        @Override public void unload(File folder){
            openCopyDelay.stop();
            bank = new CommentBank();
            folderScanned = false;
            fireChanged(false);
        }
        @Override public void write(File folder, Config config){
            config.set("comments", bank.toYAML());
        }
        @Override public boolean isEmpty(){
            return bank.isEmpty();
        }
    };

    private EvaluationComments(){
    }

    public static CommentBank getBank(){
        return bank;
    }
    public static ReadOnlyLongProperty revisionProperty(){
        return revision;
    }
    public static void fireChanged(boolean save){
        if(save) EvaluationFolders.requestSave(FOLDER_PART);
        revision.set(revision.get() + 1);
    }

    // EVENTS

    // The edition of the open copy is loaded (its grade scale is complete).
    public static void onEditionLoaded(){
        scanOpenCopy();
        if(!folderScanned && EvaluationFolders.getActiveFolder() != null){
            folderScanned = true;
            scanOtherCopies();
        }
    }
    // Reads all the copies again (menu of the section).
    public static void rescanAll(){
        folderScanned = false;
        onEditionLoaded();
    }
    // The open copy was edited: its comments are read again a bit later.
    public static void onEditionChanged(){
        if(!Platform.isFxApplicationThread()){
            Platform.runLater(EvaluationComments::onEditionChanged);
            return;
        }
        if(isOpenCopyInActiveFolder()) openCopyDelay.playFromStart();
    }
    // The comments of the open copy are read now if they were waiting to be (e.g. before suggesting comments).
    public static void scanPendingNow(){
        if(openCopyDelay.getStatus() != javafx.animation.Animation.Status.RUNNING) return;
        openCopyDelay.stop();
        scanOpenCopy();
    }
    // The copy was closed: reads it from its edit file, which may not have saved the last changes.
    public static void onCopyClosed(File pdf){
        openCopyDelay.stop();
        File folder = EvaluationFolders.getActiveFolder();
        if(folder == null || pdf == null || !folder.equals(pdf.getAbsoluteFile().getParentFile())) return;
        ExerciseContext context = ExerciseContext.current();
        try{
            List<CommentBank.Occurrence> occurrences = readFromDisk(pdf, context);
            if(bank.updateCopy(pdf.getName(), occurrences, System.currentTimeMillis())) fireChanged(true);
        }catch(Exception e){
            Log.eNotified(e, "Unable to read the comments of " + pdf.getName());
        }
    }

    // Opens a copy at a page (-1: where it was left), or scrolls to the page if it is the open copy.
    public static void openCopyAt(File copy, int page){
        if(!copy.exists()){
            MainWindow.footerBar.showToast(javafx.scene.paint.Color.web("#6a1b1b"), javafx.scene.paint.Color.WHITE,
                    fr.clementgre.pdf4teachers.panel.FooterBar.ToastDuration.MEDIUM, TR.tr("notes.goTo.missingFile", copy.getName()));
            return;
        }
        if(MainWindow.mainScreen.hasDocument(false) && MainWindow.mainScreen.document.getFile().getAbsoluteFile().equals(copy.getAbsoluteFile())){
            if(page >= 0 && page < MainWindow.mainScreen.document.getPagesNumber())
                MainWindow.mainScreen.zoomOperator.scrollToPage(MainWindow.mainScreen.document.getPage(page));
            return;
        }
        MainWindow.filesTab.openFileNonDir(copy);
        MainWindow.mainScreen.openFile(copy, true, page);
    }
    
    // SCANS

    private static boolean isOpenCopyInActiveFolder(){
        File folder = EvaluationFolders.getActiveFolder();
        return folder != null && MainWindow.mainScreen.hasDocument(false)
                && folder.equals(MainWindow.mainScreen.document.getFile().getAbsoluteFile().getParentFile());
    }

    private static void scanOpenCopy(){
        if(!isOpenCopyInActiveFolder() || GradeTreeView.getTotal() == null) return;
        ExerciseContext context = ExerciseContext.current();

        List<ExerciseLocator.Grade> grades = GradeTreeView.getGradesArray(GradeTreeView.getTotal()).stream()
                .map(GradeTreeItem::getCore)
                .map(grade -> new ExerciseLocator.Grade(grade.getPath(), grade.getPageNumber(), grade.getRealY()))
                .toList();
        ArrayList<CommentBank.Occurrence> occurrences = new ArrayList<>();
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            for(Element element : page.getElements()){
                if(!(element instanceof TextElement text) || element instanceof ScoredCommentElement || text.isMark()) continue;
                if(text.getText() == null || text.getText().isBlank()) continue;
                String exercise = ExerciseLocator.locate(text.getPageNumber(), text.getRealY(), text.getGradeCommentPath(), grades, context.pages(), context.order());
                occurrences.add(new CommentBank.Occurrence(text.getText(), text.getPageNumber(), text.getRealX(), text.getRealY(), exercise, getStyle(text), text.getGradeCommentPath()));
            }
        }
        if(bank.updateCopy(MainWindow.mainScreen.document.getFile().getName(), occurrences, System.currentTimeMillis())) fireChanged(true);
    }

    // Reads the comments of the other copies of the folder in background.
    private static void scanOtherCopies(){
        File folder = EvaluationFolders.getActiveFolder();
        File openCopy = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile().getAbsoluteFile() : null;
        ExerciseContext context = ExerciseContext.current();

        new Thread(() -> {
            File[] pdfs = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
            if(pdfs == null) return;
            LinkedHashMap<String, List<CommentBank.Occurrence>> copies = new LinkedHashMap<>();
            Set<String> existing = new HashSet<>();
            for(File pdf : pdfs){
                existing.add(pdf.getName());
                if(pdf.getAbsoluteFile().equals(openCopy)) continue; // Followed live
                try{
                    copies.put(pdf.getName(), readFromDisk(pdf, context));
                }catch(Exception e){
                    Log.e("Unable to read the comments of " + pdf.getName() + ": " + e.getMessage());
                }
            }
            Platform.runLater(() -> {
                if(!folder.equals(EvaluationFolders.getActiveFolder())) return;
                boolean changed = bank.retainCopies(existing);
                long now = System.currentTimeMillis();
                for(Map.Entry<String, List<CommentBank.Occurrence>> copy : copies.entrySet()){
                    changed |= bank.updateCopy(copy.getKey(), copy.getValue(), now);
                }
                if(changed) fireChanged(true);
            });
        }, "Evaluation comments reader").start();
    }

    private static List<CommentBank.Occurrence> readFromDisk(File pdf, ExerciseContext context) throws Exception{
        File editFile = Edition.getEditFile(pdf);
        if(!editFile.exists()) return List.of();
        Config config = new Config(editFile);
        config.load();
        return CopyComments.read(config.base, context.pages(), context.order());
    }

    static CommentBank.Style getStyle(TextElement text){
        return new CommentBank.Style(text.getFont().getFamily(), text.getFont().getSize(),
                FontUtils.getFontWeight(text.getFont()) == FontWeight.BOLD, FontUtils.getFontPosture(text.getFont()) == FontPosture.ITALIC,
                text.getColor().toString(), text.getTextMaxWidth());
    }

    // Exercises of the open grade scale, in order, and the page set for each one (footer bar "Pages").
    public record ExerciseContext(List<String> order, Map<String, Integer> pages) {
        public static ExerciseContext current(){
            ArrayList<String> order = new ArrayList<>();
            HashMap<String, Integer> pages = new HashMap<>();
            if(GradeTreeView.getTotal() == null || MainWindow.footerBar == null) return new ExerciseContext(order, pages);
            for(int i = 0; i < GradeTreeView.getTotal().getChildren().size(); i++){
                String name = ((GradeTreeItem) GradeTreeView.getTotal().getChildren().get(i)).getCore().getName();
                order.add(name);
                OptionalInt page = MainWindow.footerBar.getExercisePageIndex(MainWindow.footerBar.getExerciseKey(i));
                if(page.isPresent()) pages.put(name, page.getAsInt());
            }
            return new ExerciseContext(order, pages);
        }
    }
}
