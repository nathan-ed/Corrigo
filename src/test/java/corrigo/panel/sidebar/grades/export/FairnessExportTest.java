/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.export;

import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FairnessExportTest {

    private static final DecimalFormat FORMAT = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.FRANCE));

    private static Map<String, Object> grade(String parent, String name, int index, double value, double total){
        LinkedHashMap<String, Object> grade = new LinkedHashMap<>();
        grade.put("parentPath", parent);
        grade.put("name", name);
        grade.put("index", index);
        grade.put("value", value);
        grade.put("total", total);
        return grade;
    }
    private static Map<String, Object> text(String text, Object... keys){
        LinkedHashMap<String, Object> element = new LinkedHashMap<>();
        element.put("x", 80000);
        element.put("y", 40000);
        element.put("text", text);
        for(int i = 0; i < keys.length; i += 2) element.put((String) keys[i], keys[i + 1]);
        return element;
    }
    // A copy: Ex 2 (a /2, b /2) and PNF /2, the text of Ex 2, the texts on its pages
    private static Map<String, Object> copy(double a, double b, String text, Map<String, Double> computed, Map<String, List<Object>> texts){
        LinkedHashMap<String, Object> base = new LinkedHashMap<>();
        base.put("grades", List.of(grade("", "Total", 0, a + b + 2, 6), grade("\\Total", "Ex 2", 0, a + b, 4),
                grade("\\Total\\Ex 2", "a", 0, a, 2), grade("\\Total\\Ex 2", "b", 1, b, 2), grade("\\Total", "PNF", 1, 2, 2)));
        base.put("corrections", Map.of("\\Total\\Ex 2", Map.of("text", text, "ids", List.of(), "computed", computed)));
        base.put("texts", texts);
        return base;
    }
    private static String tr(String key){
        return key + (key.equals("fairness.typed") || key.equals("fairness.marks") ? " {0}" : "");
    }

    @Test
    void anonymousGradesLinesAndFairness(){
        Map<String, Object> first = copy(1.5, 1, "Correct\nNotation -0.5 (unité oubliée)\n\nFaux",
                Map.of("\\Total\\Ex 2\\a", 1.5, "\\Total\\Ex 2\\b", 0.0), // b: 1 typed by hand over the lines (0)
                Map.of("page0", List.of(text("PNF", "x", 3199, "y", 130459), text("1. I", "x", 3199, "y", 140000)),
                        "page2", List.of(text("PNF : unités", "pnfExercise", "Ex 2", "correctionLine", "p1"), text("Bravo, Rose et Blanc !"))));
        Map<String, Object> second = copy(1, 0, "Correct\nNotation -1\n\nPas de réponse",
                Map.of("\\Total\\Ex 2\\a", 1.0, "\\Total\\Ex 2\\b", 0.0), Map.of());
        String export = FairnessExport.build(List.of(first, second), "PNF", FORMAT, FairnessExportTest::tr);

        // The copies are numbered (their names are only in the names of their files); the texts as written, nothing replaced
        assertTrue(export.contains("## Copie 01 — 4,5 / 6"));
        assertTrue(export.contains("## Copie 02 — 3 / 6"));
        assertTrue(export.contains("  - Notation -0.5 (unité oubliée)"));
        assertTrue(export.contains("Bravo, Rose et Blanc !"));
        // The grade scale, the grades, where the points start from, a grade typed by hand
        assertTrue(export.contains("- Ex 2 : /4 (a /2, b /2)"));
        assertTrue(export.contains("- a : 1,5 / 2 — fairness.fromMax"));
        assertTrue(export.contains("- b : 1 / 2 — fairness.fromZero — fairness.typed 0"));
        // The presentation mark (not the table of the first page)
        assertTrue(export.contains("fairness.marks 1 : PNF : unités (Ex 2)"));
        assertFalse(export.contains("1. I"));
        // The same words with other points on two copies: first, with the copies
        assertTrue(export.contains("- ⚠ « Notation » — −0,5 : Copie 01 — −1 : Copie 02"), export);
        assertTrue(export.contains("« Correct » — fairness.allPoints : Copie 01, Copie 02"));
    }
}
