/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentCatalog;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Use;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TagEditFilesTest {

    private static final NumberFormat FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final String COPY = "01_A.pdf";
    private static final String Q2 = "\\Total\\Q2";

    // An edit file with the grade scale Total > Q1 (2 points), Q2 (3 points), Q2 graded 3 by hand
    private static Map<String, Object> editFile(){
        LinkedHashMap<String, Object> base = new LinkedHashMap<>();
        ArrayList<Object> grades = new ArrayList<>();
        grades.add(grade("Total", "", 5, 5, 0));
        grades.add(grade("Q1", "\\Total", 2, 2, 1));
        grades.add(grade("Q2", "\\Total", 3, 3, 1));
        base.put("grades", grades);
        base.put("texts", new LinkedHashMap<String, Object>());
        return base;
    }
    private static Map<String, Object> grade(String name, String parent, double value, double total, int page){
        LinkedHashMap<String, Object> grade = new LinkedHashMap<>();
        grade.put("name", name);
        grade.put("parentPath", parent);
        grade.put("value", value);
        grade.put("total", total);
        grade.put("page", page);
        grade.put("x", 150000);
        grade.put("y", 20000);
        return grade;
    }
    private static List<Map<String, Object>> tagElements(Map<String, Object> base){
        return ScoredCommentGrades.getPlacedComments(base).stream()
                .filter(e -> TagEditFiles.isTagElementId(String.valueOf(e.get(ScoredCommentGrades.KEY_ID)))).toList();
    }
    private static double value(Map<String, Object> base, String name){
        return ScoredCommentGrades.getGrades(base).stream().filter(g -> name.equals(g.get("name")))
                .map(g -> ((Number) g.get("value")).doubleValue()).findFirst().orElseThrow();
    }
    private static boolean sync(Map<String, Object> base, EvaluationTags tags){
        boolean changed = TagEditFiles.sync(base, tags, COPY, FORMAT);
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        return changed;
    }

    @Test
    void writesPointsOnlyOrTheCommentWithThePoints(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Q2", "Sign error", Kind.MISTAKE);
        Tag nice = tags.create("Q2", "Nice", Kind.METHOD);
        tags.setScoring(sign, 1.0, null);
        tags.setScoring(nice, 0.5, "Well done");
        assertEquals("−1", TagEditFiles.render(sign, FORMAT));
        assertEquals("Well done (+0.5)", TagEditFiles.render(nice, FORMAT));
        tags.setScoring(nice, null, "Well done");
        assertEquals("Well done", TagEditFiles.render(nice, FORMAT), "Never the name, no (0)");
    }

    @Test
    void eachOccurrenceWithPointsCountsInItsSubGrade(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Q2", "Sign error", Kind.MISTAKE);
        tags.setScoring(sign, 1.0, null);
        Map<String, Object> base = editFile();
        tags.add(COPY, "Q2", sign, Q2, new Placement(1, 40000, 50000));
        tags.add(COPY, "Q2", sign, Q2, Placement.exercise(1));

        assertTrue(sync(base, tags));
        assertEquals(2, tagElements(base).size());
        assertEquals(1, value(base, "Q2"), "3 points, −1 twice");
        assertEquals(3, value(base, "Total"));
        assertFalse(sync(base, tags), "Already up to date");
    }

    @Test
    void aCommentWithoutPointsDoesNotChangeTheGrade(){
        EvaluationTags tags = new EvaluationTags();
        Tag remark = tags.create("Q2", "Remark", Kind.METHOD);
        tags.setScoring(remark, null, "Think of a drawing");
        Map<String, Object> base = editFile();
        tags.add(COPY, "Q2", remark, Q2, new Placement(1, 40000, 50000));
        assertTrue(sync(base, tags));
        Map<String, Object> element = tagElements(base).getFirst();
        assertEquals("Think of a drawing", element.get("text"));
        assertEquals(true, element.get(ScoredCommentGrades.KEY_NO_POINTS));
        assertEquals(3, value(base, "Q2"), "The typed value stays");
    }

    @Test
    void aTagWithoutPointsNorCommentWritesNothing(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Q2", "Chain rule", Kind.METHOD);
        Map<String, Object> base = editFile();
        tags.add(COPY, "Q2", chain, Q2, new Placement(1, 40000, 50000));
        assertFalse(sync(base, tags));
        assertTrue(tagElements(base).isEmpty());
    }

    @Test
    void changesOfTheTagAreWrittenAndRemovedOccurrencesErased(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Q2", "Sign error", Kind.MISTAKE);
        tags.setScoring(sign, 1.0, null);
        Map<String, Object> base = editFile();
        Use first = tags.add(COPY, "Q2", sign, Q2, new Placement(1, 40000, 50000));
        tags.add(COPY, "Q2", sign, Q2, new Placement(1, 40000, 90000));
        sync(base, tags);
        assertEquals(1, value(base, "Q2"));

        tags.setScoring(sign, 0.5, "Sign");
        assertTrue(sync(base, tags));
        assertEquals("Sign (−0.5)", tagElements(base).getFirst().get("text"));
        assertEquals(2, value(base, "Q2"));

        tags.remove(COPY, first.id());
        sync(base, tags);
        assertEquals(1, tagElements(base).size());
        assertEquals(2.5, value(base, "Q2"));

        tags.delete(sign);
        sync(base, tags);
        assertTrue(tagElements(base).isEmpty());
        assertEquals(3, value(base, "Q2"), "No mistake left: the value typed before");
    }

    @Test
    void whatWasChangedOnTheCopyOnlyIsKept(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Q2", "Sign error", Kind.MISTAKE);
        tags.setScoring(sign, 1.0, "Sign");
        Map<String, Object> base = editFile();
        tags.add(COPY, "Q2", sign, Q2, new Placement(1, 40000, 50000));
        sync(base, tags);
        Map<String, Object> element = tagElements(base).getFirst();
        element.put(ScoredCommentGrades.KEY_LOCAL_POINTS, true);
        element.put(ScoredCommentGrades.KEY_POINTS, -0.5);

        tags.setScoring(sign, 2.0, "Sign!");
        sync(base, tags);
        element = tagElements(base).getFirst();
        assertEquals("Sign! (−0.5)", element.get("text"));
        assertEquals(2.5, value(base, "Q2"));
    }

    @Test
    void aDuplicatedElementIsRemoved(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Q2", "Sign error", Kind.MISTAKE);
        tags.setScoring(sign, 1.0, null);
        Map<String, Object> base = editFile();
        tags.add(COPY, "Q2", sign, Q2, new Placement(1, 40000, 50000));
        sync(base, tags);
        @SuppressWarnings("unchecked")
        List<Object> page = (List<Object>) ((Map<String, Object>) base.get("texts")).get("page1");
        page.add(new LinkedHashMap<>(tagElements(base).getFirst()));
        assertEquals(2, tagElements(base).size());
        assertTrue(sync(base, tags));
        assertEquals(1, tagElements(base).size());
        assertEquals(2, value(base, "Q2"));
    }

    // Total > Q3 > a (2 points), b (2 points), both graded by hand
    private static Map<String, Object> editFileWithSubGrades(){
        LinkedHashMap<String, Object> base = new LinkedHashMap<>();
        ArrayList<Object> grades = new ArrayList<>();
        grades.add(grade("Total", "", 4, 4, 0));
        grades.add(grade("Q3", "\\Total", 4, 4, 1));
        grades.add(grade("a", "\\Total\\Q3", 2, 2, 1));
        grades.add(grade("b", "\\Total\\Q3", 2, 2, 1));
        base.put("grades", grades);
        base.put("texts", new LinkedHashMap<String, Object>());
        return base;
    }

    @Test
    void aTagCanCountOnSeveralSubGrades(){
        EvaluationTags tags = new EvaluationTags();
        Tag expand = tags.create("Q3", "Expanded", Kind.MISTAKE);
        tags.setScoring(expand, 1.0, "Factorise instead");
        LinkedHashMap<String, Double> points = new LinkedHashMap<>(); // In the order of the grade scale
        points.put("\\Total\\Q3\\a", 1.0);
        points.put("\\Total\\Q3\\b", 0.5);
        tags.setGradePoints(expand, "Q3", points);
        Map<String, Object> base = editFileWithSubGrades();
        Use use = tags.add(COPY, "Q3", expand, "\\Total\\Q3\\a", new Placement(1, 40000, 50000));

        List<EvaluationTags.Target> targets = tags.getTargets(use);
        assertEquals(2, targets.size());
        assertEquals("Factorise instead", targets.getFirst().comment(), "The comment once");
        assertNull(targets.get(1).comment());
        assertTrue(sync(base, tags));
        assertEquals(2, tagElements(base).size());
        assertEquals(1, value(base, "a"));
        assertEquals(1.5, value(base, "b"));
        assertEquals(2.5, value(base, "Total"));
        assertEquals(Set.of("Factorise instead (−1)", "−0.5"), Set.copyOf(tagElements(base).stream().map(e -> e.get("text")).toList()));
        assertEquals(use.id(), TagEditFiles.getUseId(String.valueOf(tagElements(base).get(1).get(ScoredCommentGrades.KEY_ID))));

        // Back to its sub-grade only: the second element is removed
        tags.setGradePoints(expand, "Q3", Map.of());
        sync(base, tags);
        assertEquals(1, tagElements(base).size());
        assertEquals(1, value(base, "a"));
        assertEquals(2, value(base, "b"), "No points left on b: the value typed before");
    }

    @Test
    void pointsBySubGradeOfAnotherExerciseDoNotApply(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Q3", "Sign", Kind.MISTAKE);
        tags.setGradePoints(sign, "Q3", Map.of("\\Total\\Q3\\a", 1.0));
        Use inQ2 = tags.add(COPY, "Q2", sign, Q2, new Placement(1, 1, 1));
        assertTrue(tags.getTargets(inQ2).isEmpty(), "No points nor comment for Q2");
        tags.setScoring(sign, 2.0, null);
        assertEquals(List.of(new EvaluationTags.Target("", Q2, -2.0, null)), tags.getTargets(inQ2));
    }
}
