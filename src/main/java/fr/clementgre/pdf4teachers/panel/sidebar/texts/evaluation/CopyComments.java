/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;

import java.util.*;

// Reads the comments of a copy from its edit file (YAML), and the exercise of each one. Does not depend on JavaFX.
public final class CopyComments {

    // Keys of the text elements data (see TextElement)
    static final String KEY_GRADE_COMMENT = "gradeComment";
    static final String KEY_MARK = "mark";

    private CopyComments(){
    }

    public static List<CommentBank.Occurrence> read(Map<String, Object> editBase, Map<String, Integer> exercisePages, List<String> exerciseOrder){
        List<ExerciseLocator.Grade> grades = ScoredCommentGrades.getGrades(editBase).stream()
                .map(grade -> new ExerciseLocator.Grade(ScoredCommentGrades.getGradePath(grade), (int) number(grade.get("page")), number(grade.get("y"))))
                .toList();

        ArrayList<CommentBank.Occurrence> occurrences = new ArrayList<>();
        if(!(editBase.get("texts") instanceof Map<?, ?> pages)) return occurrences;
        for(Map.Entry<?, ?> page : pages.entrySet()){
            int pageIndex = parsePage(String.valueOf(page.getKey()));
            if(pageIndex < 0 || !(page.getValue() instanceof List<?> texts)) continue;
            for(Object item : texts){
                if(!(item instanceof Map<?, ?> text)) continue;
                if(!isComment(text)) continue;
                String gradeComment = text.get(KEY_GRADE_COMMENT) instanceof String path ? path : null;
                double y = number(text.get("y"));
                String exercise = ExerciseLocator.locate(pageIndex, y, gradeComment, grades, exercisePages, exerciseOrder);
                occurrences.add(new CommentBank.Occurrence(String.valueOf(text.get("text")), pageIndex, number(text.get("x")), y, exercise, readStyle(text)));
            }
        }
        return occurrences;
    }

    // Texts written by the teacher: not the scored comments (listed in the grading panel) nor the computed marks.
    static boolean isComment(Map<?, ?> text){
        if(!(text.get("text") instanceof String value) || value.isBlank()) return false;
        if(text.containsKey(ScoredCommentGrades.KEY_ID)) return false;
        return !Boolean.TRUE.equals(text.get(KEY_MARK));
    }

    private static CommentBank.Style readStyle(Map<?, ?> text){
        if(!(text.get("font") instanceof String font)) return null;
        return new CommentBank.Style(font, number(text.get("size")), Boolean.TRUE.equals(text.get("bold")), Boolean.TRUE.equals(text.get("italic")),
                text.get("color") instanceof String color ? color : "0x000000ff", number(text.get("maxWidth")));
    }

    // "page3" -> 3
    static int parsePage(String key){
        if(!key.startsWith("page")) return -1;
        try{
            return Integer.parseInt(key.substring(4));
        }catch(NumberFormatException e){
            return -1;
        }
    }
    private static double number(Object value){
        if(value instanceof Number number) return number.doubleValue();
        if(value != null){
            try{
                return Double.parseDouble(value.toString());
            }catch(NumberFormatException ignored){
            }
        }
        return 0;
    }
}
