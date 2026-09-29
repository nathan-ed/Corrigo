/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.datasaving.evaluation.EvaluationFolders;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Use;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * The methods and mistakes of the open evaluation (EvaluationTags), stored in its folder ("tags.yml").
 */
public final class ExerciseTags {

    // Grid size of a page (Element.GRID_WIDTH / GRID_HEIGHT)
    private static final double GRID_WIDTH = 165400, GRID_HEIGHT = 233900;

    private static EvaluationTags data = new EvaluationTags();
    private static final SimpleLongProperty revision = new SimpleLongProperty();

    public static final EvaluationFolders.Part FOLDER_PART = new EvaluationFolders.Part() {
        @Override public String getFileName(){
            return "tags";
        }
        @Override public void load(File folder, Config config){
            data = EvaluationTags.fromYAML(config.base);
            fireChanged(false);
            TagMarkers.update();
        }
        @Override public void unload(File folder){
            data = new EvaluationTags();
            TagReview.stop();
            fireChanged(false);
            TagMarkers.update();
        }
        @Override public void write(File folder, Config config){
            config.base.putAll(data.toYAML());
        }
        @Override public boolean isEmpty(){
            return data.isEmpty();
        }
    };

    private ExerciseTags(){
    }

    public static EvaluationTags getData(){
        return data;
    }
    public static ReadOnlyLongProperty revisionProperty(){
        return revision;
    }
    // Copies whose methods and mistakes changed: what is written on them is updated with the next fireChanged(true)
    private static final Set<String> dirtyCopies = new HashSet<>();

    public static void fireChanged(boolean save){
        if(save){
            EvaluationFolders.requestSave(FOLDER_PART);
            ArrayList<String> copies = new ArrayList<>(dirtyCopies);
            dirtyCopies.clear();
            TagScoring.sync(copies);
        }
        revision.set(revision.get() + 1);
        if(save) TagMarkers.update();
    }

    // The edition of the open copy is loaded: what is written for its methods and mistakes is checked.
    public static void onEditionLoaded(){
        syncWhenReady(50);
    }
    // Once the document is ready (its undo engine is set after the edition is loaded)
    private static void syncWhenReady(int tries){
        Platform.runLater(() -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            if(MainWindow.mainScreen.getUndoEngine() == null){
                if(tries > 0) syncWhenReady(tries - 1);
                return;
            }
            TagScoring.syncOpenCopy();
            TagMarkers.update();
        });
    }

    // CONTEXT

    // File name of the open copy, if it is in the active evaluation folder.
    public static String getOpenCopy(){
        File folder = EvaluationFolders.getActiveFolder();
        if(folder == null || !MainWindow.mainScreen.hasDocument(false)) return null;
        File file = MainWindow.mainScreen.document.getFile().getAbsoluteFile();
        return folder.equals(file.getParentFile()) ? file.getName() : null;
    }
    // Exercise being graded (footer bar), or null.
    public static GradeTreeItem getExercise(){
        return MainWindow.footerBar == null ? null : MainWindow.footerBar.getSelectedExercise();
    }
    // The PDF files of the active evaluation folder, by name.
    public static List<File> getFolderCopies(){
        File folder = EvaluationFolders.getActiveFolder();
        if(folder == null) return List.of();
        File[] pdfs = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        if(pdfs == null) return List.of();
        return Arrays.stream(pdfs).sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    // The spot under the mouse if it is on a page, or else the page of the exercise.
    public static Placement getMousePlacement(){
        if(MainWindow.mainScreen.hasDocument(false)){
            PageRenderer page = MainWindow.mainScreen.document.getLastCursorOverPageObject();
            if(page != null && page.isHover()){
                return new Placement(page.getPage(), page.toGridX(page.getMouseX()), page.toGridY(page.getMouseY()));
            }
        }
        return getExercisePlacement();
    }
    // Top of the page of the exercise being graded.
    public static Placement getExercisePlacement(){
        int index = MainWindow.footerBar == null ? -1 : MainWindow.footerBar.getSelectedExerciseIndex();
        OptionalInt page = index < 0 ? OptionalInt.empty() : MainWindow.footerBar.getExercisePage(index);
        return Placement.exercise(page.orElse(0));
    }
    // Placement of a tag added from the panel (not at a spot of the copy)
    public static boolean isExerciseSpot(Placement placement){
        return placement.isExerciseSpot();
    }
    public static boolean isNear(Placement a, Placement b){
        return a.isNear(b);
    }

    // ACTIONS (all the changes of the occurrences and of the tags go through here: the copies are updated)

    // The sub-grade the points of a new occurrence count on: the one active in the grading panel if it is of this
    // exercise, else the first sub-grade of the exercise ("" if there is none).
    public static String getTargetGrade(String exercise){
        if(MainWindow.gradingPanel != null){
            String active = MainWindow.gradingPanel.getActiveLeafPath(exercise);
            if(active != null) return active;
        }
        return getFirstGrade(exercise);
    }
    // The first sub-grade of the exercise, the exercise if it has none, or "" if there is no such exercise
    public static String getFirstGrade(String exercise){
        if(GradeTreeView.getTotal() == null) return "";
        for(javafx.scene.control.TreeItem<String> child : GradeTreeView.getTotal().getChildren()){
            if(!(child instanceof GradeTreeItem item) || !item.getCore().getName().equals(exercise)) continue;
            return GradeTreeView.getGradesArray(item).stream().filter(g -> !g.hasSubGrade()).findFirst().orElse(item).getCore().getPath();
        }
        return "";
    }

    // The sub-grade of the exercise whose grade is the closest to a spot of the copy (same page), or null
    public static String getGradeNear(String exercise, Placement placement){
        if(placement.isExerciseSpot() || GradeTreeView.getTotal() == null) return null;
        for(javafx.scene.control.TreeItem<String> child : GradeTreeView.getTotal().getChildren()){
            if(!(child instanceof GradeTreeItem item) || !item.getCore().getName().equals(exercise)) continue;
            return GradeTreeView.getGradesArray(item).stream()
                    .filter(g -> !g.hasSubGrade() && g.getCore().getPageNumber() == placement.page())
                    .min(Comparator.comparingDouble(g -> Math.abs(g.getCore().getRealY() - placement.y())))
                    .map(g -> g.getCore().getPath()).orElse(null);
        }
        return null;
    }

    // Adds an occurrence of the tag on a copy. Its points count on the sub-grade whose grade is the closest to where it
    // is put, or else on the active sub-grade of the exercise.
    public static Use addOccurrence(String copy, String exercise, Tag tag, Placement placement){
        String near = getGradeNear(exercise, placement);
        Use use = data.add(copy, exercise, tag, near != null ? near : getTargetGrade(exercise), placement);
        dirtyCopies.add(copy);
        fireChanged(true);
        return use;
    }
    public static Use removeOccurrence(String copy, String useId){
        Use removed = data.remove(copy, useId);
        dirtyCopies.add(copy);
        fireChanged(true);
        return removed;
    }
    // Puts back an occurrence removed before (undo): same id, sub-grade and place.
    public static void restoreOccurrence(Use use){
        data.restore(use);
        dirtyCopies.add(use.copy());
        fireChanged(true);
    }
    public static List<Use> removeAllOccurrences(String copy, String exercise, Tag tag){
        List<Use> removed = data.removeAll(copy, exercise, tag);
        dirtyCopies.add(copy);
        fireChanged(true);
        return removed;
    }
    public static void changeOccurrence(String copy, String useId, Tag tag){
        data.change(copy, useId, tag);
        dirtyCopies.add(copy);
        fireChanged(true);
    }
    public static void moveOccurrence(String copy, String useId, Placement placement){
        data.move(copy, useId, placement); // What is written on the copy stays where it is
        fireChanged(true);
    }
    // Points (positive, or null) and comment of a tag: written on all the copies that have it.
    public static void setScoring(Tag tag, Double points, String comment){
        dirtyCopies.addAll(data.getCopies(tag));
        data.setScoring(tag, points, comment);
        fireChanged(true);
    }
    // Same, with the points by sub-grade of an exercise (empty: they count on the sub-grade they are put for)
    public static void setScoring(Tag tag, Double points, String comment, String exercise, Map<String, Double> gradePoints){
        dirtyCopies.addAll(data.getCopies(tag));
        data.setScoring(tag, points, comment);
        if(exercise != null) data.setGradePoints(tag, exercise, gradePoints);
        fireChanged(true);
    }
    public static void setKind(Tag tag, Kind kind){
        dirtyCopies.addAll(data.getCopies(tag)); // The sign of the points and the color change
        data.setKind(tag, kind);
        fireChanged(true);
    }
    public static void deleteTag(Tag tag){
        dirtyCopies.addAll(data.getCopies(tag));
        data.delete(tag);
        fireChanged(true);
    }

    // SCORES

    // Points of the exercise on each copy of the folder (the open copy from the document), computed in background.
    public static void loadScores(GradeTreeItem exercise, Consumer<Map<String, Double>> callback){
        String path = exercise.getCore().getPath();
        String openCopy = getOpenCopy();
        double openValue = exercise.getCore().getValue();
        List<File> copies = getFolderCopies();
        new Thread(() -> {
            HashMap<String, Double> scores = new HashMap<>();
            for(File copy : copies){
                if(copy.getName().equals(openCopy)){
                    if(openValue >= 0) scores.put(openCopy, openValue);
                    continue;
                }
                try{
                    Double value = Edition.loadGradeValues(copy).get(path);
                    if(value != null && value >= 0) scores.put(copy.getName(), value);
                }catch(Exception e){
                    Log.w("Unable to read the grades of " + copy.getName() + ": " + e.getMessage());
                }
            }
            Platform.runLater(() -> callback.accept(scores));
        }, "Exercise scores").start();
    }
}
