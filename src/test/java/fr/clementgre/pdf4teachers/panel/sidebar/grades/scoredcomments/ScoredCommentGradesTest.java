/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

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

        ScoredCommentGrades.recomputeGrades(base, new ScoredCommentCatalog(), name -> name.startsWith("Bonus"));

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
    @Test
    void usesBaseOfCatalog(){
        Map<String, Object> base = edition(List.of(placed("e1", "\\Total\\Ex 1\\a", "Good", 1)));
        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        catalog.setBase("\\Total\\Ex 1\\a", ScoredCommentCatalog.Base.ZERO);
        ScoredCommentGrades.recomputeGrades(base, catalog, name -> false);
        assertEquals(1, valueOf(base, "\\Total\\Ex 1\\a"));
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
