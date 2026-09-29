/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.datasaving.evaluation.EvaluationFolders;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation.EvaluationComments;
import fr.clementgre.pdf4teachers.utils.dialogs.AlertIconType;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Review of the copies of a tag (or of the copies without method): the previous/next copy shortcuts and buttons
 * only go through these copies, each one opened where the tag was put, until the review is stopped.
 */
public final class TagReview {

    // label: shown in the banner. copies: file names with the page to open (-1: the exercise page).
    public record Review(String label, List<String> copies, Map<String, Placement> placements) {}

    private static final SimpleObjectProperty<Review> review = new SimpleObjectProperty<>();

    private TagReview(){
    }

    public static ReadOnlyObjectProperty<Review> reviewProperty(){
        return review;
    }
    public static boolean isActive(){
        return review.get() != null;
    }

    public static void start(Review newReview){
        if(newReview.copies().isEmpty()) return;
        review.set(newReview);
        // Starts from the open copy if it is in the review, from the first one otherwise
        if(getIndex() < 0) open(0);
    }
    public static void stop(){
        review.set(null);
    }

    // Position of the open copy in the review, or -1.
    public static int getIndex(){
        Review current = review.get();
        String open = ExerciseTags.getOpenCopy();
        return current == null || open == null ? -1 : current.copies().indexOf(open);
    }

    // Opens the previous (-1) or next (1) copy of the review.
    public static void openNeighbor(int delta){
        Review current = review.get();
        if(current == null) return;
        int index = getIndex();
        int target = index < 0 ? 0 : index + delta;
        if(target < 0 || target >= current.copies().size()){
            MainWindow.showNotification(AlertIconType.INFORMATION, TR.tr(target < 0 ? "tags.review.first" : "tags.review.last"), 5);
            return;
        }
        open(target);
    }

    private static void open(int index){
        Review current = review.get();
        File folder = EvaluationFolders.getActiveFolder();
        if(current == null || folder == null) return;
        String copy = current.copies().get(index);
        Placement placement = current.placements().get(copy);
        int page = placement != null ? placement.page() : ExerciseTags.getExercisePlacement().page();
        EvaluationComments.openCopyAt(new File(folder, copy), page);
    }
}
