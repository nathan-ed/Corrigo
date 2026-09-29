/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoredCommentCatalogTest {

    @Test
    void roundTripsThroughYAML(){
        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        catalog.add(new ScoredComment("a", "\\Total\\Ex 1", "Sign error", -0.5, null));
        catalog.add(new ScoredComment("b", "\\Total\\Ex 2", "Nice", 1, "#00aa00"));
        catalog.setBase("\\Total\\Ex 2", ScoredCommentCatalog.Base.ZERO);

        ScoredCommentCatalog read = ScoredCommentCatalog.fromYAML(catalog.toYAML());

        assertEquals(2, read.getComments().size());
        ScoredComment b = read.get("b").orElseThrow();
        assertEquals("\\Total\\Ex 2", b.getGradePath());
        assertEquals("Nice", b.getText());
        assertEquals(1, b.getPoints());
        assertEquals("#00aa00", b.getColor());
        assertNull(read.get("a").orElseThrow().getColor());
        assertEquals(ScoredCommentCatalog.Base.ZERO, read.getBase("\\Total\\Ex 2"));
        assertEquals(ScoredCommentCatalog.Base.FULL, read.getBase("\\Total\\Ex 1"));
    }
    @Test
    void ignoresMalformedEntries(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("comments", List.of(Map.of("id", "x", "points", "bad"), Map.of("points", 1), "text"));
        data.put("bases", Map.of("\\Total", "WRONG"));
        ScoredCommentCatalog read = ScoredCommentCatalog.fromYAML(data);
        assertTrue(read.getComments().isEmpty());
        assertTrue(read.isEmpty());
    }

    @Test
    void renamesGradeAndSubGradePaths(){
        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        catalog.add(new ScoredComment("a", "\\Total\\Ex 1\\a", "", -1, null));
        catalog.add(new ScoredComment("b", "\\Total\\Ex 10", "", -1, null));
        catalog.setBase("\\Total\\Ex 1\\a", ScoredCommentCatalog.Base.ZERO);

        assertTrue(catalog.renameGradePath("\\Total\\Ex 1", "\\Total\\Exercise 1"));

        assertEquals("\\Total\\Exercise 1\\a", catalog.get("a").orElseThrow().getGradePath());
        assertEquals("\\Total\\Ex 10", catalog.get("b").orElseThrow().getGradePath());
        assertEquals(ScoredCommentCatalog.Base.ZERO, catalog.getBase("\\Total\\Exercise 1\\a"));
    }

    @Test
    void findsClosestEvaluation(){
        ScoredCommentCatalog first = new ScoredCommentCatalog();
        first.add(new ScoredComment("\\Total\\Ex 1", "A", -1, null));
        ScoredCommentCatalog second = new ScoredCommentCatalog();
        second.add(new ScoredComment("\\Total\\Ex 1", "B", -1, null));

        LinkedHashMap<String, ScoredCommentCatalog> catalogs = new LinkedHashMap<>();
        catalogs.put("Ex 1 | Ex 2", first);
        catalogs.put("Ex 1 | Ex 2 | Ex 3", second);
        catalogs.put("Other", new ScoredCommentCatalog());

        assertSame(second, ScoredCommentCatalog.findClosest(catalogs, "Ex 1 | Ex 2 | Ex 3 | Ex 4"));
        assertNull(ScoredCommentCatalog.findClosest(catalogs, "Q1 | Q2"));
    }
}
