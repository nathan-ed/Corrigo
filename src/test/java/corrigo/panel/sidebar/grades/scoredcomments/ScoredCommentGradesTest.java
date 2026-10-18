/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.scoredcomments;

import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class ScoredCommentGradesTest {

    private static final DecimalFormat FORMAT = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US));

    @Test
    void computesLeafFromFullMarks(){
        assertEquals(3.5, ScoredCommentGrades.computeLeaf(4, ScoredCommentCatalog.Base.FULL, List.of(-0.5)));
        assertEquals(3, ScoredCommentGrades.computeLeaf(4, ScoredCommentCatalog.Base.FULL, List.of(-1.5, 0.5)));
    }
    @Test
    void computesLeafFromZero(){
        assertEquals(1.5, ScoredCommentGrades.computeLeaf(4, ScoredCommentCatalog.Base.ZERO, List.of(1d, 0.5)));
    }
    @Test
    void clampsLeafBetweenZeroAndTotal(){
        assertEquals(4, ScoredCommentGrades.computeLeaf(4, ScoredCommentCatalog.Base.FULL, List.of(1d)));
        assertEquals(0, ScoredCommentGrades.computeLeaf(4, ScoredCommentCatalog.Base.FULL, List.of(-3d, -3d)));
        assertEquals(0, ScoredCommentGrades.computeLeaf(4, ScoredCommentCatalog.Base.ZERO, List.of(-1d)));
    }

    @Test
    void rendersCheckWithoutPoints(){
        assertEquals(ScoredCommentGrades.CHECK, ScoredCommentGrades.render(ScoredCommentGrades.CHECK, 2, FORMAT));
    }
    @Test
    void rendersSignedPoints(){
        assertEquals("Sign error (−0.5)", ScoredCommentGrades.render("Sign error", -0.5, FORMAT));
        assertEquals("Nice (+1)", ScoredCommentGrades.render("Nice", 1, FORMAT));
        assertEquals("Neutral (0)", ScoredCommentGrades.render("Neutral", 0, FORMAT));
        assertEquals("−2", ScoredCommentGrades.render("", -2, FORMAT));
    }
    @Test
    void parsesRenderedText(){
        ScoredCommentGrades.Parsed parsed = ScoredCommentGrades.parse("Sign error (−0.5)").orElseThrow();
        assertEquals("Sign error", parsed.comment());
        assertEquals(-0.5, parsed.points());

        parsed = ScoredCommentGrades.parse("Multi\nline (+1,25)").orElseThrow();
        assertEquals("Multi\nline", parsed.comment());
        assertEquals(1.25, parsed.points());

        parsed = ScoredCommentGrades.parse("Typed with dash (-1)").orElseThrow();
        assertEquals(-1, parsed.points());

        parsed = ScoredCommentGrades.parse("−2").orElseThrow();
        assertEquals("", parsed.comment());
        assertEquals(-2, parsed.points());
    }
    @Test
    void unsignedNumbersAreNotPoints(){
        assertTrue(ScoredCommentGrades.parse("See question (2)").isEmpty());
        assertTrue(ScoredCommentGrades.parse("No points").isEmpty());
    }

    // EDIT FILES

    private static Map<String, Object> grade(String parentPath, String name, double value, double total, boolean fromComments){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("parentPath", parentPath);
        data.put("name", name);
        data.put("value", value);
        data.put("total", total);
        data.put("index", 0);
        if(fromComments) data.put(ScoredCommentGrades.KEY_VALUE_SOURCE, ScoredCommentGrades.VALUE_SOURCE_COMMENTS);
        return data;
    }
    private static Map<String, Object> placed(String id, String gradePath, String comment, double points){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("x", 0);
        data.put("y", 0);
        data.put("text", ScoredCommentGrades.render(comment, points, FORMAT));
        data.put(ScoredCommentGrades.KEY_ID, id);
        data.put(ScoredCommentGrades.KEY_GRADE_PATH, gradePath);
        data.put(ScoredCommentGrades.KEY_COMMENT, comment);
        data.put(ScoredCommentGrades.KEY_POINTS, points);
        data.put(ScoredCommentGrades.KEY_LOCAL_TEXT, false);
        data.put(ScoredCommentGrades.KEY_LOCAL_POINTS, false);
        return data;
    }
    // Total > Ex 1 > (a, b) + Ex 2 + Bonus
    private static Map<String, Object> edition(List<Map<String, Object>> texts){
        HashMap<String, Object> base = new HashMap<>();
        base.put("grades", new ArrayList<>(List.of(
                grade("", "Total", 7, 10, false),
                grade("\\Total", "Ex 1", 4, 6, false),
                grade("\\Total\\Ex 1", "a", 4, 4, true),
                grade("\\Total\\Ex 1", "b", 0, 2, false),
                grade("\\Total", "Ex 2", 3, 4, false),
                grade("\\Total", "Bonus", -1, 1, false)
        )));
        LinkedHashMap<String, Object> textsSection = new LinkedHashMap<>();
        textsSection.put("page0", new ArrayList<>(texts));
        base.put("texts", textsSection);
        return base;
    }
    private static double valueOf(Map<String, Object> base, String path){
        return ScoredCommentGrades.getGrades(base).stream()
                .filter(g -> ScoredCommentGrades.getGradePath(g).equals(path))
                .mapToDouble(g -> ((Number) g.get("value")).doubleValue())
                .findFirst().orElseThrow();
    }

    @Test
    void recomputesLeavesAndParentSums(){
        Map<String, Object> base = edition(List.of(
                placed("e1", "\\Total\\Ex 1\\a", "Sign", -0.5),
                placed("e1", "\\Total\\Ex 1\\a", "Sign", -0.5),
                placed("e2", "\\Total\\Ex 1\\a", "Nice", 0.25)));

        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        catalog.setBase("\\Total\\Ex 1\\a", ScoredCommentCatalog.Base.FULL); // Chosen: a point added does not start from 0
        ScoredCommentGrades.recomputeGrades(base, catalog, name -> name.startsWith("Bonus"));

        assertEquals(3.25, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals(3.25, valueOf(base, "\\Total\\Ex 1"));
        assertEquals(6.25, valueOf(base, "\\Total"));
        // The bonus total is not counted
        double[] root = ScoredCommentGrades.getRootGrade(base);
        assertEquals(10, root[1]);
    }
    @Test
    void manualGradesAreNotRecomputed(){
        Map<String, Object> base = edition(List.of(placed("e1", "\\Total\\Ex 1\\b", "Sign", -0.5)));
        ScoredCommentGrades.recomputeGrades(base, null, name -> false);
        assertEquals(0, valueOf(base, "\\Total\\Ex 1\\b"));
    }
    @Test
    void leafWithoutCommentsIsUnfilled(){
        Map<String, Object> base = edition(List.of());
        ScoredCommentGrades.recomputeGrades(base, null, name -> false);
        assertEquals(-1, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals(0, valueOf(base, "\\Total\\Ex 1"));
    }
    private static void recompute(Map<String, Object> base, ScoredCommentCatalog catalog){
        ScoredCommentGrades.recomputeGrades(base, catalog, name -> false);
    }
    @Test
    void startsFromTheTypedValue(){
        ScoredCommentGrades.Leaf typed = new ScoredCommentGrades.Leaf("\\Total\\Ex 1\\a", 3, 1.5, false, -1, -1, List.of(-0.5), false, true);
        assertEquals(1.5, ScoredCommentGrades.getStart(typed, ScoredCommentCatalog.Base.FULL, true));
        assertEquals(1.5, ScoredCommentGrades.getStart(typed, ScoredCommentCatalog.Base.ZERO, true));
        ScoredCommentGrades.Leaf nothing = new ScoredCommentGrades.Leaf("\\Total\\Ex 1\\a", 3, -1, false, -1, -1, List.of(1d), false, true);
        assertEquals(3, ScoredCommentGrades.getStart(nothing, ScoredCommentCatalog.Base.FULL, true));
        assertEquals(0, ScoredCommentGrades.getStart(nothing, ScoredCommentCatalog.Base.ZERO, true));
        // In an edit file: the value before comments
        Map<String, Object> base = edition(List.of(placed("e1", "\\Total\\Ex 1\\a", "Sign", -0.5)));
        gradeData(base, "a").put(ScoredCommentGrades.KEY_VALUE_BEFORE, 2.5);
        recompute(base, null);
        assertEquals(2, valueOf(base, "\\Total\\Ex 1\\a"));
    }
    private static Object autoOf(Map<String, Object> base){
        return gradeData(base, "Ex 1").get(ScoredCommentGrades.KEY_AUTO_MODE);
    }
    @Test
    void firstMistakeCountsByMistakes(){
        Map<String, Object> base = computedA();
        place(base, placed("m", "\\Total\\Ex 1\\a", "Sign", -1));
        recompute(base, new ScoredCommentCatalog());
        assertEquals(3, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals("FULL", autoOf(base));
        // A method then gives a point back (from the maximum, kept)
        place(base, placed("ok", "\\Total\\Ex 1\\a", "Idea", 0.5));
        recompute(base, new ScoredCommentCatalog());
        assertEquals(3.5, valueOf(base, "\\Total\\Ex 1\\a"));
        // The mistake removed: still by mistakes, the method cannot go above the maximum
        unplace(base, "m");
        recompute(base, new ScoredCommentCatalog());
        assertEquals(4, valueOf(base, "\\Total\\Ex 1\\a"));
        // Nothing left: not graded, the automatic mode is forgotten
        unplace(base, "ok");
        recompute(base, new ScoredCommentCatalog());
        assertEquals(-1, valueOf(base, "\\Total\\Ex 1\\a"));
        assertNull(autoOf(base));
    }
    @Test
    void firstMethodCountsByCriteria(){
        Map<String, Object> base = computedA();
        gradeData(base, "b").put("value", -1.0);
        place(base, placed("ok", "\\Total\\Ex 1\\a", "Idea", 1));
        recompute(base, new ScoredCommentCatalog());
        assertEquals(1, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals(0, valueOf(base, "\\Total\\Ex 1\\b")); // By criteria: what is shown counts
        assertEquals("ZERO", autoOf(base));
        // A mistake then removes from it, also on another sub-grade of the exercise
        place(base, placed("m", "\\Total\\Ex 1\\a", "Sign", -0.5));
        place(base, placed("m2", "\\Total\\Ex 1\\b", "Sign", -0.5));
        recompute(base, new ScoredCommentCatalog());
        assertEquals(0.5, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals(0, valueOf(base, "\\Total\\Ex 1\\b"));
        // Chosen for the class: by mistakes
        ScoredCommentCatalog mistakes = new ScoredCommentCatalog();
        mistakes.setBase("\\Total\\Ex 1", ScoredCommentCatalog.Base.FULL);
        recompute(base, mistakes);
        assertEquals(4, valueOf(base, "\\Total\\Ex 1\\a")); // 4 + 1 − 0.5, at most 4
        assertEquals(1.5, valueOf(base, "\\Total\\Ex 1\\b"));
    }
    @Test
    void byCriteriaFromZeroAndWhatIsShownCounts(){
        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        catalog.setBase("\\Total\\Ex 1", ScoredCommentCatalog.Base.ZERO);
        Map<String, Object> base = computedA();
        gradeData(base, "b").put("value", -1.0);
        recompute(base, catalog);
        assertEquals(-1, valueOf(base, "\\Total\\Ex 1\\a")); // Not started
        assertEquals(-1, valueOf(base, "\\Total\\Ex 1\\b"));
        place(base, placed("ok", "\\Total\\Ex 1\\a", "Idea", 1));
        place(base, placed("m", "\\Total\\Ex 1\\a", "Sign", -0.5));
        recompute(base, catalog);
        assertEquals(0.5, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals(0, valueOf(base, "\\Total\\Ex 1\\b")); // Started: b, with nothing, is 0
        assertEquals(0.5, valueOf(base, "\\Total\\Ex 1"));
        // Nothing left: not started any more
        unplace(base, "ok");
        unplace(base, "m");
        recompute(base, catalog);
        assertEquals(-1, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals(-1, valueOf(base, "\\Total\\Ex 1\\b"));
    }
    @Test
    void modeOfTheCopyComesFirst(){
        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        catalog.setBase("\\Total\\Ex 1", ScoredCommentCatalog.Base.ZERO);
        Map<String, Object> base = computedA();
        gradeData(base, "Ex 1").put(ScoredCommentGrades.KEY_MODE, "FULL");
        place(base, placed("m", "\\Total\\Ex 1\\a", "Sign", -1));
        recompute(base, catalog);
        assertEquals(3, valueOf(base, "\\Total\\Ex 1\\a"));
        assertEquals(ScoredCommentCatalog.Base.ZERO, ScoredCommentGrades.getChosenMode(null, catalog, "\\Total\\Ex 1\\a"));
        assertEquals(ScoredCommentCatalog.Base.FULL, ScoredCommentGrades.getChosenMode(ScoredCommentCatalog.Base.FULL, catalog, "\\Total\\Ex 1\\a"));
        assertNull(ScoredCommentGrades.getChosenMode(null, new ScoredCommentCatalog(), "\\Total\\Ex 1\\a"));
    }
    @Test
    void findsExerciseOfGrade(){
        assertEquals("\\Total\\Ex 1", ScoredCommentGrades.getExercisePath("\\Total\\Ex 1\\a"));
        assertEquals("\\Total\\Ex 1", ScoredCommentGrades.getExercisePath("\\Total\\Ex 1\\1\\a"));
        assertEquals("\\Total\\Ex 2", ScoredCommentGrades.getExercisePath("\\Total\\Ex 2"));
    }
    private static Map<String, Object> gradeData(Map<String, Object> base, String name){
        return ScoredCommentGrades.getGrades(base).stream().filter(g -> name.equals(g.get("name"))).findFirst().orElseThrow();
    }
    private static void place(Map<String, Object> base, Map<String, Object> comment){
        ((List<Object>) ((Map<?, ?>) base.get("texts")).get("page0")).add(comment);
    }
    private static void unplace(Map<String, Object> base, String id){
        ((List<Map<String, Object>>) ((Map<?, ?>) base.get("texts")).get("page0")).removeIf(c -> id.equals(c.get(ScoredCommentGrades.KEY_ID)));
    }
    private static Map<String, Object> computedA(){ // a: 4 points, computed from its comments, nothing typed
        Map<String, Object> base = edition(List.of());
        gradeData(base, "a").put(ScoredCommentGrades.KEY_VALUE_BEFORE, -1.0);
        return base;
    }
    @Test
    void tickedCriterionDoesNotDependOnTheOrder(){
        // A mistake, then the criterion ticked: the same as the tick then the mistake (4 − 0.5)
        Map<String, Object> base = computedA();
        place(base, placed("m", "\\Total\\Ex 1\\a", "Sign", -0.5));
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        assertEquals(3.5, valueOf(base, "\\Total\\Ex 1\\a"));
        place(base, placed("t", "\\Total\\Ex 1\\a", ScoredCommentGrades.CHECK, 4));
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        assertEquals(3.5, valueOf(base, "\\Total\\Ex 1\\a"));
        // The tick removed: back to the mistake alone
        unplace(base, "t");
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        assertEquals(3.5, valueOf(base, "\\Total\\Ex 1\\a"));
    }
    @Test
    void keepsGradesOfPreviousVersion(){
        // Previous version: typed 1, then a method +1 counted from the maximum, saved 4 / 4 (now: 1 + 1 = 2)
        Map<String, Object> base = edition(List.of(placed("ok", "\\Total\\Ex 1\\a", "Method", 1)));
        gradeData(base, "a").put(ScoredCommentGrades.KEY_VALUE_BEFORE, 1.0);
        assertEquals(List.of("\\Total\\Ex 1\\a"), ScoredCommentGrades.keepStoredValues(base, new ScoredCommentCatalog()));
        assertEquals(4.0, gradeData(base, "a").get(ScoredCommentGrades.KEY_START));
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        assertEquals(4, valueOf(base, "\\Total\\Ex 1\\a"));
        // Read again: nothing more to write
        assertTrue(ScoredCommentGrades.keepStoredValues(base, new ScoredCommentCatalog()).isEmpty());
        // A mistake added later counts from the kept start
        place(base, placed("m", "\\Total\\Ex 1\\a", "Sign", -1.5));
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        assertEquals(3.5, valueOf(base, "\\Total\\Ex 1\\a"));
        // No point left: the typed value is given back
        ((List<?>) ((Map<?, ?>) base.get("texts")).get("page0")).clear();
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        assertEquals(1, valueOf(base, "\\Total\\Ex 1\\a"));
    }
    @Test
    void keptGradeComesBackWithTheModeByMistakes(){
        Map<String, Object> base = edition(List.of(placed("ok", "\\Total\\Ex 1\\a", "Method", 1)));
        gradeData(base, "a").put(ScoredCommentGrades.KEY_VALUE_BEFORE, 1.0);
        ScoredCommentGrades.keepStoredValues(base, new ScoredCommentCatalog());
        ScoredCommentCatalog criteria = new ScoredCommentCatalog();
        criteria.setBase("\\Total\\Ex 1", ScoredCommentCatalog.Base.ZERO);
        ScoredCommentGrades.recomputeGrades(base, criteria, name -> false);
        assertEquals(2, valueOf(base, "\\Total\\Ex 1\\a")); // By criteria: typed 1 + 1
        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> false);
        assertEquals(4, valueOf(base, "\\Total\\Ex 1\\a")); // Back by mistakes: as saved
    }
    @Test
    void usesBaseOfCatalog(){
        Map<String, Object> base = edition(List.of(placed("e1", "\\Total\\Ex 1\\a", "Good", 1)));
        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        catalog.setBase("\\Total\\Ex 1\\a", ScoredCommentCatalog.Base.ZERO);
        ScoredCommentGrades.recomputeGrades(base, catalog, name -> false);
        assertEquals(1, valueOf(base, "\\Total\\Ex 1\\a"));
    }

    @Test
    void doesNotKeepCurrentGrades(){
        Map<String, Object> current = edition(List.of(placed("m", "\\Total\\Ex 1\\a", "Sign", -0.5)));
        gradeData(current, "a").put("value", 3.5);
        assertTrue(ScoredCommentGrades.keepStoredValues(current, new ScoredCommentCatalog()).isEmpty());
        assertFalse(gradeData(current, "a").containsKey(ScoredCommentGrades.KEY_START));
    }

    @Test
    void appliesEntryKeepingLocalChanges(){
        Map<String, Object> local = placed("e1", "\\Total\\Ex 1\\a", "My own words", -0.5);
        local.put(ScoredCommentGrades.KEY_LOCAL_TEXT, true);
        Map<String, Object> base = edition(List.of(placed("e1", "\\Total\\Ex 1\\a", "Sign", -0.5), local,
                placed("other", "\\Total\\Ex 1\\a", "Other", -1)));

        ScoredComment entry = new ScoredComment("e1", "\\Total\\Ex 1\\a", "Sign error", -1, "#00aa00");
        ScoredCommentGrades.ApplyResult result = ScoredCommentGrades.applyEntry(base, entry, FORMAT);

        assertEquals(2, result.linked());
        assertEquals(1, result.withLocalChanges());
        List<Map<String, Object>> placed = ScoredCommentGrades.getPlacedComments(base);
        assertEquals("Sign error (−1)", placed.get(0).get("text"));
        assertEquals("My own words (−1)", placed.get(1).get("text"));
        assertEquals("0x00aa00ff", placed.get(1).get("color"));
        assertEquals("Other (−1)", placed.get(2).get("text"));
    }
    @Test
    void removesEntryOrKeepsItAsText(){
        Map<String, Object> base = edition(List.of(placed("e1", "\\Total\\Ex 1\\a", "Sign", -0.5), placed("e2", "\\Total\\Ex 1\\a", "Nice", 1)));
        assertEquals(1, ScoredCommentGrades.removeEntry(base, "e1", true));
        assertEquals(1, ScoredCommentGrades.getPlacedComments(base).size());

        assertEquals(1, ScoredCommentGrades.removeEntry(base, "e2", false));
        assertTrue(ScoredCommentGrades.getPlacedComments(base).isEmpty());
        // The first one is still there as a plain text
        assertEquals(1, ((List<?>) ((Map<?, ?>) base.get("texts")).get("page0")).size());
    }

    @Test
    void readsPlacedCommentsWithTheirPage(){
        Map<String, Object> base = edition(List.of(placed("e1", "\\Total\\Ex 1\\a", "Sign", -0.5)));
        @SuppressWarnings("unchecked")
        Map<String, Object> texts = (Map<String, Object>) base.get("texts");
        texts.put("page3", new ArrayList<>(List.of(placed("e2", "\\Total\\Ex 2", "Unit", -1), Map.of("text", "plain"))));
        
        List<ScoredCommentGrades.PlacedData> placed = ScoredCommentGrades.getPlacedCommentsWithPage(base);
        assertEquals(2, placed.size());
        assertEquals(3, placed.get(1).page());
        assertEquals("Unit", placed.get(1).getComment());
        assertEquals(-1, placed.get(1).getPoints());
    }
    @Test
    void matchesAllWordsIgnoringCaseAndAccents(){
        assertTrue(ScoredCommentGrades.matchesQuery("Erreur de signe", "Ex 1 › a", "SIGNE erreur"));
        assertTrue(ScoredCommentGrades.matchesQuery("Unité manquante", "Ex 2", "unite"));
        assertTrue(ScoredCommentGrades.matchesQuery("Sign error", "Ex 2", "ex 2"));
        assertFalse(ScoredCommentGrades.matchesQuery("Sign error", "Ex 1", "unit"));
    }
    
    @Test
    void readsSignatureFromTopLevelGrades(){
        Map<String, Object> base = edition(List.of());
        assertEquals("Ex 1 | Ex 2 | Bonus", ScoredCommentGrades.getSignature(base));
    }
}
