/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Target;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Use;

import java.text.NumberFormat;
import java.util.*;

/**
 * What a method or a mistake writes on a copy, in its edit file (YAML): for each occurrence of a tag with points or a
 * comment, and for each sub-grade it counts on (EvaluationTags.getTargets), a scored comment element ("comment (−1)",
 * "−1" or the comment only) linked to the occurrence by its id, counted in the grade of this sub-grade.
 * Does not depend on JavaFX.
 */
public final class TagEditFiles {

    public static final String ID_PREFIX = "tag:";
    public static final String FONT = "Open Sans";
    public static final double FONT_SIZE = 18;
    public static final String METHOD_COLOR = "0x2e7d32ff", MISTAKE_COLOR = "0xc62828ff";
    // Grid size of a page (Element.GRID_WIDTH / GRID_HEIGHT), and width of the comments (percent of the page width)
    private static final double GRID_WIDTH = 165400, GRID_HEIGHT = 233900, MAX_WIDTH = 30;

    private TagEditFiles(){
    }

    // Id of the element written for an occurrence: "tag:<tag id>#<occurrence id>", then "@<grade path>" for the
    // other sub-grades it counts on
    public static String elementId(Use use){
        return ID_PREFIX + use.tag() + "#" + use.id();
    }
    public static String elementId(Use use, Target target){
        return elementId(use) + (target.key().isEmpty() ? "" : "@" + target.key());
    }
    public static boolean isTagElementId(String id){
        return id != null && id.startsWith(ID_PREFIX);
    }
    public static String getUseId(String elementId){
        int index = elementId == null ? -1 : elementId.indexOf('#');
        if(index < 0) return "";
        int end = elementId.indexOf('@', index);
        return end < 0 ? elementId.substring(index + 1) : elementId.substring(index + 1, end);
    }

    // Text written on the copy: "comment (−1)", "−1", or the comment only
    public static String render(Target target, NumberFormat format){
        String comment = target.comment() == null ? "" : target.comment();
        if(target.points() == null) return comment;
        return ScoredCommentGrades.render(comment, target.points(), format);
    }
    // Text written by an occurrence on its sub-grade (without points by sub-grade)
    public static String render(Tag tag, NumberFormat format){
        String comment = tag.getComment() == null ? "" : tag.getComment();
        if(!tag.hasPoints()) return comment;
        return ScoredCommentGrades.render(comment, tag.getSignedPoints(), format);
    }
    // What an occurrence of the tag writes in this exercise: "comment (−1)", or by sub-grade "a: comment (−1) · b: −0.5".
    // Empty if nothing is written.
    public static String describe(Tag tag, String exercise, NumberFormat format){
        Map<String, Double> byGrade = tag.getGradePoints(exercise);
        if(byGrade.isEmpty()) return tag.hasPoints() || tag.getComment() != null ? render(tag, format) : "";
        EvaluationTags sample = new EvaluationTags();
        Tag copy = sample.create(tag.getExercise(), tag.getName(), tag.getKind());
        sample.setScoring(copy, tag.getPoints(), tag.getComment());
        sample.setGradePoints(copy, exercise, byGrade);
        StringJoiner written = new StringJoiner("  ·  ");
        for(Target target : sample.getTargets(sample.add("", exercise, copy, "", EvaluationTags.Placement.exercise(0)))){
            String grade = target.grade().substring(target.grade().lastIndexOf('\\') + 1);
            written.add(grade + ": " + render(target, format));
        }
        return written.toString();
    }
    public static String getColor(Tag tag){
        return tag.getKind() == Kind.MISTAKE ? MISTAKE_COLOR : METHOD_COLOR;
    }
    public static double getPoints(Target target){
        return target.points() == null ? 0 : target.points();
    }

    // An element to write: for an occurrence, on one of the sub-grades it counts on
    public record Expected(Use use, Tag tag, Target target) {}

    // The elements the occurrences of a copy write, by element id
    public static LinkedHashMap<String, Expected> getExpected(EvaluationTags data, String copy){
        LinkedHashMap<String, Expected> expected = new LinkedHashMap<>();
        for(Use use : data.getUses(copy)){
            for(Target target : data.getTargets(use)) expected.put(elementId(use, target), new Expected(use, data.getTag(use.tag()), target));
        }
        return expected;
    }

    /**
     * Makes the elements written for the methods and mistakes in an edit file match the occurrences of the copy:
     * adds the missing ones, updates their text and points (except what was changed on the copy only), removes the
     * others. The grades are not recomputed (ScoredCommentGrades.recomputeGrades). Returns true if something changed.
     */
    public static boolean sync(Map<String, Object> editBase, EvaluationTags data, String copy, NumberFormat format){
        LinkedHashMap<String, Expected> expected = getExpected(data, copy);
        boolean changed = false;
        Set<String> present = new HashSet<>();
        Map<?, ?> texts = editBase.get("texts") instanceof Map<?, ?> map ? map : null;
        if(texts != null){
            for(Object pageTexts : texts.values()){
                if(!(pageTexts instanceof List<?> list)) continue;
                Iterator<?> iterator = list.iterator();
                while(iterator.hasNext()){
                    if(!(iterator.next() instanceof Map<?, ?> element)) continue;
                    String id = String.valueOf(element.get(ScoredCommentGrades.KEY_ID));
                    if(!isTagElementId(id)) continue;
                    Expected wanted = expected.get(id);
                    // Not an occurrence any more, written twice, or for another sub-grade now (written again next to its grade)
                    if(wanted == null || !wanted.target().grade().equals(String.valueOf(element.get(ScoredCommentGrades.KEY_GRADE_PATH)))
                            || !present.add(id)){
                        iterator.remove();
                        changed = true;
                        continue;
                    }
                    changed |= update(editBase, cast(element), wanted, format);
                }
            }
        }
        for(Map.Entry<String, Expected> entry : expected.entrySet()){
            if(present.contains(entry.getKey())) continue;
            add(editBase, entry.getKey(), entry.getValue(), format);
            changed = true;
        }
        return changed;
    }

    private static boolean update(Map<String, Object> editBase, Map<String, Object> element, Expected wanted, NumberFormat format){
        Map<String, Object> before = new HashMap<>(element);
        Target target = wanted.target();
        boolean counted = !Boolean.TRUE.equals(element.get(ScoredCommentGrades.KEY_NO_POINTS));
        boolean localText = Boolean.TRUE.equals(element.get(ScoredCommentGrades.KEY_LOCAL_TEXT));
        boolean localPoints = Boolean.TRUE.equals(element.get(ScoredCommentGrades.KEY_LOCAL_POINTS));
        element.put(ScoredCommentGrades.KEY_GRADE_PATH, target.grade());
        if(!localText) element.put(ScoredCommentGrades.KEY_COMMENT, target.comment() == null ? "" : target.comment());
        if(!localPoints){
            element.put(ScoredCommentGrades.KEY_POINTS, getPoints(target));
            if(target.points() != null) element.remove(ScoredCommentGrades.KEY_NO_POINTS);
            else element.put(ScoredCommentGrades.KEY_NO_POINTS, true);
        }
        element.put("color", getColor(wanted.tag()));
        String comment = String.valueOf(element.getOrDefault(ScoredCommentGrades.KEY_COMMENT, ""));
        boolean noPoints = Boolean.TRUE.equals(element.get(ScoredCommentGrades.KEY_NO_POINTS));
        double points = element.get(ScoredCommentGrades.KEY_POINTS) instanceof Number number ? number.doubleValue() : 0;
        element.put("text", noPoints ? comment : ScoredCommentGrades.render(comment, points, format));
        // Starts counting points: the sub-grade is computed from its comments (a typed value is kept otherwise)
        if(!noPoints && !counted) countFromComments(editBase, target.grade());
        return !before.equals(element);
    }

    // A new element, next to the spot of the occurrence, or in a column next to its grade.
    private static void add(Map<String, Object> editBase, String id, Expected wanted, NumberFormat format){
        Use use = wanted.use();
        Target target = wanted.target();
        int page = use.placement().page();
        double x, y;
        if(!use.placement().isExerciseSpot() && target.key().isEmpty()){
            // Under the line of the spot: not over what the student wrote
            x = use.placement().x() - GRID_WIDTH * .01;
            y = use.placement().y() + GRID_HEIGHT * .012;
        }else{
            Map<String, Object> grade = findGrade(editBase, target.grade());
            if(grade != null){
                page = (int) number(grade.get("page"));
                x = number(grade.get("x")) - GRID_WIDTH * (MAX_WIDTH / 100 + .015);
                y = number(grade.get("y")) + GRID_HEIGHT * .025 * countTagElements(editBase, page);
            }else{
                x = GRID_WIDTH * .05;
                y = GRID_HEIGHT * .1;
            }
        }
        LinkedHashMap<String, Object> element = new LinkedHashMap<>();
        element.put("x", (long) Math.max(0, Math.min(GRID_WIDTH * .95, x)));
        element.put("y", (long) Math.max(0, Math.min(GRID_HEIGHT * .97, y)));
        element.put("color", getColor(wanted.tag()));
        element.put("font", FONT);
        element.put("size", FONT_SIZE);
        element.put("bold", false);
        element.put("italic", false);
        element.put("text", render(target, format));
        element.put("maxWidth", MAX_WIDTH);
        element.put(ScoredCommentGrades.KEY_ID, id);
        element.put(ScoredCommentGrades.KEY_GRADE_PATH, target.grade());
        element.put(ScoredCommentGrades.KEY_COMMENT, target.comment() == null ? "" : target.comment());
        element.put(ScoredCommentGrades.KEY_POINTS, getPoints(target));
        element.put(ScoredCommentGrades.KEY_LOCAL_TEXT, false);
        element.put(ScoredCommentGrades.KEY_LOCAL_POINTS, false);
        if(target.points() == null) element.put(ScoredCommentGrades.KEY_NO_POINTS, true);

        Map<String, Object> texts = cast(editBase.computeIfAbsent("texts", k -> new LinkedHashMap<String, Object>()));
        Object list = texts.get("page" + page);
        if(!(list instanceof List<?>)){
            list = new ArrayList<>();
            texts.put("page" + page, list);
        }
        @SuppressWarnings("unchecked") List<Object> pageTexts = (List<Object>) list;
        pageTexts.add(element);
        if(target.points() != null) countFromComments(editBase, target.grade());
    }

    // The sub-grade is computed from its comments from now on (as when a comment is placed on the open copy)
    private static void countFromComments(Map<String, Object> editBase, String gradePath){
        Map<String, Object> grade = findGrade(editBase, gradePath);
        if(grade == null || ScoredCommentGrades.VALUE_SOURCE_COMMENTS.equals(grade.get(ScoredCommentGrades.KEY_VALUE_SOURCE))) return;
        grade.put(ScoredCommentGrades.KEY_VALUE_BEFORE, grade.get("value") instanceof Number value ? value.doubleValue() : -1d);
        grade.put(ScoredCommentGrades.KEY_VALUE_SOURCE, ScoredCommentGrades.VALUE_SOURCE_COMMENTS);
    }

    private static Map<String, Object> findGrade(Map<String, Object> editBase, String gradePath){
        if(gradePath == null || gradePath.isEmpty()) return null;
        return ScoredCommentGrades.getGrades(editBase).stream()
                .filter(grade -> gradePath.equals(ScoredCommentGrades.getGradePath(grade)))
                .findFirst().orElse(null);
    }
    private static int countTagElements(Map<String, Object> editBase, int page){
        if(!(editBase.get("texts") instanceof Map<?, ?> texts) || !(texts.get("page" + page) instanceof List<?> list)) return 0;
        return (int) list.stream().filter(e -> e instanceof Map<?, ?> m && isTagElementId(String.valueOf(m.get(ScoredCommentGrades.KEY_ID)))).count();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object map){
        return (Map<String, Object>) map;
    }
    private static double number(Object value){
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
