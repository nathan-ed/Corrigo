/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.NewEvaluationPlan.Copy;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.NewEvaluationPlan.Problem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewEvaluationPlanTest {

    @Test
    void readsOneStudentPerLineAndSkipsNumbersColumns(){
        assertEquals(List.of("Alpha", "Bravo Charlie", "Delta"), NewEvaluationPlan.parseStudents("  Alpha\n\nBravo Charlie \r\n3;Delta\n"));
        assertEquals(List.of("Echo"), NewEvaluationPlan.parseStudents("12\tEcho\tFoxtrot"));
    }

    @Test
    void namesTheCopiesWithTheirNumber(){
        assertEquals("07_ALPHA.pdf", NewEvaluationPlan.fileName("alpha", 6, 20, true, true));
        assertEquals("007_ALPHA_BRAVO.pdf", NewEvaluationPlan.fileName(" Alpha  Bravo ", 6, 120, true, true));
        assertEquals("Élise.pdf", NewEvaluationPlan.fileName("Él/ise", 0, 3, false, false));
        assertEquals("01_ÉLISE.pdf", NewEvaluationPlan.fileName("Élise", 0, 3, true, true));
    }

    @Test
    void cutsTheScanRegularly(){
        List<Copy> copies = NewEvaluationPlan.plan(List.of("Alpha", "Bravo", "Charlie"), 4, true, true);
        assertEquals(new Copy("02_BRAVO.pdf", "Bravo", 4, 7), copies.get(1));
        assertNull(NewEvaluationPlan.check(copies, 12));
        assertEquals(Problem.EXTRA_PAGES, NewEvaluationPlan.check(copies, 13));
        assertFalse(NewEvaluationPlan.isBlocking(Problem.EXTRA_PAGES));
        assertEquals(Problem.NOT_ENOUGH_PAGES, NewEvaluationPlan.check(copies, 11));
        assertTrue(NewEvaluationPlan.isBlocking(Problem.NOT_ENOUGH_PAGES));
    }

    @Test
    void guessesThePagesAndFindsProblems(){
        assertEquals(4, NewEvaluationPlan.guessPagesPerCopy(48, 12));
        assertEquals(4, NewEvaluationPlan.guessPagesPerCopy(49, 12));
        assertEquals(0, NewEvaluationPlan.guessPagesPerCopy(10, 0));
        assertEquals(Problem.NO_STUDENT, NewEvaluationPlan.check(List.of(), 10));
        assertEquals(Problem.DUPLICATE_NAMES, NewEvaluationPlan.check(NewEvaluationPlan.plan(List.of("Alpha", "alpha"), 1, false, true), 2));
    }
}
