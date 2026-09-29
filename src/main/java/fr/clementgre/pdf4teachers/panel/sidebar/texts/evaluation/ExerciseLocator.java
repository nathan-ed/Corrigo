/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Finds the exercise a comment is about, from where it is on the copy:
 * 1. A comment written for a grade (grading panel) belongs to the exercise of this grade.
 * 2. Otherwise, on a page that has sub-grades: the exercise of the closest sub-grade above the comment (or the first one below).
 * 3. Otherwise, the exercise whose page (set in the footer bar "Pages") is the last one before or on the comment page.
 * 4. Otherwise, the exercise of the last sub-grade placed on a previous page.
 * Returns null when the comment is about no exercise (general comment).
 */
public final class ExerciseLocator {

    // A comment placed a bit higher than the grade of its line still belongs to it (2% of Element.GRID_HEIGHT, a page height).
    static final double SAME_LINE_TOLERANCE = 233900 * 0.02;

    // A grade of the copy: its path (\Total\Ex 1\a), page index and y position (grid units).
    public record Grade(String path, int page, double y) {}

    private ExerciseLocator(){
    }

    /**
     * @param gradeCommentPath Path of the grade the comment was written for, or null.
     * @param grades All the grades of the copy (the root and the exercises included).
     * @param exercisePages Page index of each exercise, by exercise name (may be empty).
     * @param exerciseOrder The exercise names in the order of the grade scale.
     */
    public static String locate(int page, double y, String gradeCommentPath, List<Grade> grades,
                                Map<String, Integer> exercisePages, List<String> exerciseOrder){
        if(gradeCommentPath != null && !gradeCommentPath.isEmpty()){
            String exercise = getExercise(gradeCommentPath);
            if(exercise != null) return exercise;
        }

        List<Grade> leaves = grades.stream().filter(grade -> getExercise(grade.path()) != null && isLeaf(grade, grades)).toList();

        List<Grade> onPage = leaves.stream().filter(grade -> grade.page() == page).toList();
        if(!onPage.isEmpty()){
            Grade closest = onPage.stream()
                    .filter(grade -> grade.y() <= y + SAME_LINE_TOLERANCE)
                    .max(Comparator.comparingDouble(Grade::y))
                    .orElseGet(() -> onPage.stream().min(Comparator.comparingDouble(Grade::y)).orElseThrow());
            return getExercise(closest.path());
        }

        String mapped = null;
        int mappedPage = -1;
        for(String exercise : exerciseOrder){ // The last one wins when several exercises start on the same page
            Integer exercisePage = exercisePages.get(exercise);
            if(exercisePage != null && exercisePage <= page && exercisePage >= mappedPage){
                mapped = exercise;
                mappedPage = exercisePage;
            }
        }
        if(mapped != null) return mapped;

        return leaves.stream()
                .filter(grade -> grade.page() < page)
                .max(Comparator.comparingInt(Grade::page).thenComparingDouble(Grade::y))
                .map(grade -> getExercise(grade.path()))
                .orElse(null);
    }

    // "Ex 1" for "\Total\Ex 1\a" or "\Total\Ex 1"; null for the root "\Total".
    public static String getExercise(String gradePath){
        if(gradePath == null) return null;
        String[] parts = gradePath.split(Pattern.quote("\\"));
        // parts[0] is empty and parts[1] is the root
        return parts.length >= 3 ? parts[2] : null;
    }

    private static boolean isLeaf(Grade grade, List<Grade> grades){
        String prefix = grade.path() + "\\";
        return grades.stream().noneMatch(other -> other.path().startsWith(prefix));
    }
}
