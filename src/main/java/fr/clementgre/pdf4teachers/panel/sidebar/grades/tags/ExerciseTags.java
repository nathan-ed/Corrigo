/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
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
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
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
    private static final double EXERCISE_SPOT_X = GRID_WIDTH * 0.1, EXERCISE_SPOT_Y = GRID_HEIGHT * 0.12;

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
    public static void fireChanged(boolean save){
        if(save) EvaluationFolders.requestSave(FOLDER_PART);
        revision.set(revision.get() + 1);
        if(save) TagMarkers.update();
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
        return new Placement(page.orElse(0), EXERCISE_SPOT_X, EXERCISE_SPOT_Y);
    }
    // Same page, and close (2 % of the width, 1.5 % of the height)
    public static boolean isNear(Placement a, Placement b){
        return a.page() == b.page() && Math.abs(a.x() - b.x()) < GRID_WIDTH * .02 && Math.abs(a.y() - b.y()) < GRID_HEIGHT * .015;
    }
    // Placement of a tag added from the panel (not at a spot of the copy)
    public static boolean isExerciseSpot(Placement placement){
        return placement.x() == EXERCISE_SPOT_X && placement.y() == EXERCISE_SPOT_Y;
    }

    // ACTIONS

    // Adds the tag to the open copy for this exercise, or removes it. Returns true if the copy has it now.
    public static boolean toggleOnOpenCopy(String exercise, Tag tag, Placement placement){
        String copy = getOpenCopy();
        if(copy == null) return false;
        boolean has = data.toggle(copy, exercise, tag, placement);
        fireChanged(true);
        return has;
    }

    // Replaces a tag of a copy by another one, at the same spot. The copy may already have the other one: then only
    // the first one is removed. Returns true if the other one was added.
    public static boolean change(String copy, String exercise, Tag from, Tag to, Placement placement){
        boolean added = !data.has(copy, exercise, to);
        data.remove(copy, exercise, from);
        if(added) data.add(copy, exercise, to, placement);
        fireChanged(true);
        return added;
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
