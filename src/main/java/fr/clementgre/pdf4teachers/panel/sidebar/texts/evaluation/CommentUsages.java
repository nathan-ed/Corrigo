/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.datasaving.evaluation.EvaluationFolders;
import fr.clementgre.pdf4teachers.datasaving.evaluation.KnownEvaluations;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.ScoredCommentElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import javafx.application.Platform;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Finds where a comment is written: in all the copies of the evaluations opened before, and of the folders of the files list.
 * The copies are read from their edit files (the open copy from the document, with its unsaved changes).
 * Texts differing only by spaces or line breaks are the same comment.
 */
public final class CommentUsages {

    // A comment written on a copy: its page and position (grid units), and its style to draw it on the preview
    // (no text: nothing drawn). detail: shown under the preview, or null.
    public record Usage(File folder, File copy, int page, double x, double y, String exercise, String text, CommentBank.Style style, String detail) {}

    private CommentUsages(){
    }

    /**
     * Searches in background. Must be called on the JavaFX thread.
     * @param onFolder Called on the JavaFX thread with the usages of each folder that has some.
     * @param onDone Called on the JavaFX thread at the end.
     */
    public static void search(String text, Consumer<List<Usage>> onFolder, Runnable onDone){
        String target = CommentBank.normalize(text);

        // The open copy: from the document
        File openCopy = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile().getAbsoluteFile() : null;
        List<Usage> openUsages = openCopy == null ? List.of() : findInOpenCopy(openCopy, target);

        LinkedHashSet<File> folders = new LinkedHashSet<>();
        if(EvaluationFolders.getActiveFolder() != null) folders.add(EvaluationFolders.getActiveFolder());
        folders.addAll(KnownEvaluations.getFolders());
        for(File file : MainWindow.filesTab.files.getItems()){
            File folder = file.getAbsoluteFile().getParentFile();
            if(folder != null) folders.add(folder);
        }

        new Thread(() -> {
            for(File folder : folders){
                ArrayList<Usage> usages = new ArrayList<>();
                if(openCopy != null && folder.equals(openCopy.getParentFile())) usages.addAll(openUsages);
                File[] pdfs = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
                if(pdfs != null){
                    Arrays.sort(pdfs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                    for(File pdf : pdfs){
                        if(pdf.getAbsoluteFile().equals(openCopy)) continue;
                        usages.addAll(findInEditFile(folder, pdf, target));
                    }
                }
                if(!usages.isEmpty()) Platform.runLater(() -> onFolder.accept(usages));
            }
            Platform.runLater(onDone);
        }, "Comment usages search").start();
    }

    private static List<Usage> findInEditFile(File folder, File pdf, String target){
        File editFile = Edition.getEditFile(pdf);
        if(!editFile.exists()) return List.of();
        try{
            Config config = new Config(editFile);
            config.load();
            return CopyComments.read(config.base, Map.of(), List.of()).stream()
                    .filter(occurrence -> CommentBank.normalize(occurrence.text()).equals(target))
                    .map(occurrence -> new Usage(folder, pdf.getAbsoluteFile(), occurrence.page(), occurrence.x(), occurrence.y(),
                            occurrence.exercise(), occurrence.text(), occurrence.style(), null))
                    .toList();
        }catch(Exception e){
            Log.e("Unable to read the comments of " + pdf + ": " + e.getMessage());
            return List.of();
        }
    }

    private static List<Usage> findInOpenCopy(File copy, String target){
        EvaluationComments.ExerciseContext context = EvaluationComments.ExerciseContext.current();
        List<ExerciseLocator.Grade> grades = GradeTreeView.getTotal() == null ? List.of() : GradeTreeView.getGradesArray(GradeTreeView.getTotal()).stream()
                .map(GradeTreeItem::getCore)
                .map(grade -> new ExerciseLocator.Grade(grade.getPath(), grade.getPageNumber(), grade.getRealY()))
                .toList();
        ArrayList<Usage> usages = new ArrayList<>();
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            for(Element element : page.getElements()){
                if(!(element instanceof TextElement text) || element instanceof ScoredCommentElement || text.isMark()) continue;
                if(text.getText() == null || !CommentBank.normalize(text.getText()).equals(target)) continue;
                String exercise = ExerciseLocator.locate(text.getPageNumber(), text.getRealY(), text.getGradeCommentPath(), grades, context.pages(), context.order());
                usages.add(new Usage(copy.getParentFile(), copy, text.getPageNumber(), text.getRealX(), text.getRealY(), exercise,
                        text.getText(), EvaluationComments.getStyle(text), null));
            }
        }
        return usages;
    }
}
