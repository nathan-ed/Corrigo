/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;

import java.util.*;
import java.util.function.Predicate;

/**
 * Marks from 1 to 6 computed from the total grade: obtained / total * 5 + 1, rounded to the nearest half (5.75 → 6, 5.749 → 5.5).
 * Also the edit file (YAML) operations of the mark text. Does not depend on JavaFX.
 */
public class Marks {

    public static final double MIN = 1;
    public static final double MAX = 6;
    // Points that could be added to a copy to see if its mark would change
    public static final double[] RAISES = {.5, 1};

    // Floating point errors must not change the rounding: 17.5/20*5+1 is 5.375 and must give 5.5.
    private static final double EPSILON = 1e-9;

    private Marks(){
    }
    
    // Position of the mark on the copies (grid coordinates). The same for all the copies, whatever their evaluation.
    public record Position(int page, int x, int y) {
        public LinkedHashMap<String, Object> toYAML(){
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("page", page);
            data.put("x", x);
            data.put("y", y);
            return data;
        }
        // Null if the data is not a complete position.
        public static Position fromYAML(Object data){
            if(data instanceof Map<?, ?> map && map.get("page") instanceof Number page
                    && map.get("x") instanceof Number x && map.get("y") instanceof Number y){
                return new Position(page.intValue(), x.intValue(), y.intValue());
            }
            return null;
        }
    }

    public static double roundToHalf(double mark){
        return Math.floor(mark * 2 + .5 + EPSILON) / 2;
    }

    public static double compute(double value, double total){
        if(total <= 0) return MIN;
        return Math.clamp(roundToHalf(value / total * (MAX - MIN) + MIN), MIN, MAX);
    }

    /**
     * The smallest raise of RAISES that changes the mark, if any. A copy can't get more points than the total.
     */
    public static OptionalDouble getChangingRaise(double value, double total){
        double mark = compute(value, total);
        for(double raise : RAISES){
            if(value + raise > total + EPSILON) break;
            if(compute(value + raise, total) > mark) return OptionalDouble.of(raise);
        }
        return OptionalDouble.empty();
    }

    // Value of a mark text: "4.5", "4,5", or older texts like "Mark: 4.5". Empty if it has no number.
    public static OptionalDouble parseMarkText(String text){
        if(text == null) return OptionalDouble.empty();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*$").matcher(text.trim());
        if(!matcher.find()) return OptionalDouble.empty();
        return OptionalDouble.of(Double.parseDouble(matcher.group(1).replace(',', '.')));
    }
    
    // EDIT FILES (YAML)

    /**
     * A copy is graded when all its grades without sub-grades have a value (bonus grades may be empty).
     * @param isBonus tells if a grade name is a bonus grade.
     */
    public static boolean isGraded(Map<String, Object> editBase, Predicate<String> isBonus){
        List<Map<String, Object>> grades = ScoredCommentGrades.getGrades(editBase);
        if(grades.isEmpty()) return false;
        Set<String> parents = new HashSet<>();
        for(Map<String, Object> grade : grades) parents.add(String.valueOf(grade.get("parentPath")));

        for(Map<String, Object> grade : grades){
            if(parents.contains(ScoredCommentGrades.getGradePath(grade))) continue; // Has sub-grades
            if(isBonus.test(String.valueOf(grade.get("name")))) continue;
            if(!(grade.get("value") instanceof Number value) || value.doubleValue() < 0) return false;
        }
        return true;
    }

    // Removes the mark texts. Returns the number of removed texts.
    public static int removeMarkTexts(Map<String, Object> editBase){
        if(!(editBase.get("texts") instanceof Map<?, ?> texts)) return 0;
        int count = 0;
        for(Object pageTexts : texts.values()){
            if(!(pageTexts instanceof List<?> list)) continue;
            int size = list.size();
            list.removeIf(text -> text instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get(TextElement.KEY_MARK)));
            count += size - list.size();
        }
        return count;
    }

    // Replaces the mark texts by this one (text data, see TextElement.getYAMLData).
    @SuppressWarnings("unchecked")
    public static void setMarkText(Map<String, Object> editBase, int page, Map<Object, Object> textData){
        removeMarkTexts(editBase);
        textData.put(TextElement.KEY_MARK, true);
        if(!(editBase.get("texts") instanceof Map<?, ?>)) editBase.put("texts", new LinkedHashMap<String, Object>());
        Map<String, Object> texts = (Map<String, Object>) editBase.get("texts");
        Object pageTexts = texts.get("page" + page);
        if(pageTexts instanceof List<?> list) ((List<Object>) list).add(textData);
        else texts.put("page" + page, new ArrayList<>(List.of(textData)));
    }
}
