/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.export;

import corrigo.panel.sidebar.grades.export.CopyChecks.Check;
import corrigo.panel.sidebar.grades.export.CopyChecks.Kind;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CopyChecksTest {

    private static Map<String, Object> grade(String parent, String name, int index, double value, double total){
        LinkedHashMap<String, Object> grade = new LinkedHashMap<>();
        grade.put("parentPath", parent);
        grade.put("name", name);
        grade.put("index", index);
        grade.put("value", value);
        grade.put("total", total);
        return grade;
    }
    private static Map<String, Object> placed(String id){
        return new LinkedHashMap<>(Map.of("x", 80000, "y", 40000, "text", "…", "correctionLine", id));
    }
    // Ex 1 /2 (no sub-question), Ex 2 (a /2, b /2)
    private static Map<String, Object> copy(double ex1, double a, double b, Map<String, Object> corrections, List<Object> texts){
        LinkedHashMap<String, Object> base = new LinkedHashMap<>();
        base.put("grades", List.of(grade("", "Total", 0, -1, 6), grade("\\Total", "Ex 1", 0, ex1, 2), grade("\\Total", "Ex 2", 1, -1, 4),
                grade("\\Total\\Ex 2", "a", 0, a, 2), grade("\\Total\\Ex 2", "b", 1, b, 2)));
        base.put("corrections", corrections);
        base.put("texts", Map.of("page0", texts));
        return base;
    }
    private static Map<String, Object> entry(String text, List<String> ids, Map<String, Double> computed){
        return new LinkedHashMap<>(Map.of("text", text, "ids", ids, "computed", computed));
    }

    @Test
    void notStarted(){
        List<Check> checks = CopyChecks.of(copy(-1, -1, -1, Map.of(), List.of()), "PNF");
        assertEquals(1, checks.size());
        assertEquals(Kind.NOT_STARTED, checks.getFirst().kind());
        assertEquals(Kind.NOT_STARTED, CopyChecks.of(Map.of(), "PNF").getFirst().kind()); // No edit file yet
    }

    @Test
    void allInOrder(){
        Map<String, Object> corrections = Map.of(
                "\\Total\\Ex 1", entry("Correct", List.of("i1"), Map.of("\\Total\\Ex 1", 2.0)),
                "\\Total\\Ex 2", entry("Notation -0.5\n\nFaux", List.of("i2", "", "i3"), Map.of("\\Total\\Ex 2\\a", 1.5, "\\Total\\Ex 2\\b", 0.0)));
        assertEquals(List.of(), CopyChecks.of(copy(2, 1.5, 0, corrections, List.of(placed("i1"), placed("i2"), placed("i3"))), "PNF"));
    }

    @Test
    void unplacedTypedAndNotGraded(){
        Map<String, Object> corrections = Map.of(
                // A comment in parentheses is never written: not to place
                "\\Total\\Ex 1", entry("Correct\n(vu au brouillon)", List.of("i1", "i2"), Map.of("\\Total\\Ex 1", 2.0)),
                // a: 1 typed over the lines (1.5), one line not placed; b: no grade
                "\\Total\\Ex 2", entry("Notation -0.5\nPNF : unités", List.of("i3", "i4"), Map.of("\\Total\\Ex 2\\a", 1.5)));
        List<Check> checks = CopyChecks.of(copy(2, 1, -1, corrections, List.of(placed("i1"), placed("i3"))), "PNF");
        assertEquals(List.of(new Check(Kind.UNPLACED, 1, "Ex 2", "", -1, null, 1),
                new Check(Kind.TYPED, 1, "Ex 2", "a", 1, 1.5, 0),
                new Check(Kind.NOT_GRADED, 1, "Ex 2", "b", -1, null, 0)), checks);
    }

    @Test
    void typedWithoutLinesAndIdsLost(){
        // Ex 1 graded by hand before any line of its exercise; Ex 2: ids not matching (file edited by hand): nothing linked
        Map<String, Object> corrections = Map.of(
                "\\Total\\Ex 1", entry("(à revoir)", List.of("i1"), Map.of()),
                "\\Total\\Ex 2", entry("Notation -0.5\n\nFaux", List.of("i2"), Map.of("\\Total\\Ex 2\\a", 1.5, "\\Total\\Ex 2\\b", 0.0)));
        List<Check> checks = CopyChecks.of(copy(1.5, 1.5, 0, corrections, List.of(placed("i2"))), "PNF");
        assertEquals(List.of(new Check(Kind.TYPED, 0, "Ex 1", "", 1.5, null, 0),
                new Check(Kind.UNPLACED, 1, "Ex 2", "", -1, null, 2)), checks);
    }
}
