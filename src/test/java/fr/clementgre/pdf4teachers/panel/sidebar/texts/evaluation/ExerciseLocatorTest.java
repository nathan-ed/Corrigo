/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation.ExerciseLocator.Grade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExerciseLocatorTest {

    // Page 0: the table of the totals (Q1, Q2 have sub-grades placed on their pages) and PNF, which has no sub-grade.
    // Page 2: Q1 a and b. Page 3: Q2 a. Page 5: Q2 b and Q3 (no sub-grade).
    private static final List<Grade> GRADES = List.of(
            new Grade("\\Total", 0, 180000),
            new Grade("\\Total\\PNF", 0, 178000),
            new Grade("\\Total\\Q1", 0, 140000),
            new Grade("\\Total\\Q2", 0, 150000),
            new Grade("\\Total\\Q1\\a", 2, 40000),
            new Grade("\\Total\\Q1\\b", 2, 120000),
            new Grade("\\Total\\Q2\\a", 3, 60000),
            new Grade("\\Total\\Q2\\b", 5, 30000),
            new Grade("\\Total\\Q3", 5, 150000)
    );
    private static final List<String> ORDER = List.of("PNF", "Q1", "Q2", "Q3");

    private static String locate(int page, double y){
        return ExerciseLocator.locate(page, y, null, GRADES, Map.of(), ORDER);
    }

    @Test
    void exerciseOfPath(){
        assertEquals("Ex 1", ExerciseLocator.getExercise("\\Total\\Ex 1\\a"));
        assertEquals("Ex 1", ExerciseLocator.getExercise("\\Total\\Ex 1"));
        assertNull(ExerciseLocator.getExercise("\\Total"));
        assertNull(ExerciseLocator.getExercise(null));
    }

    @Test
    void commentOfAGradeBelongsToItsExercise(){
        assertEquals("Q3", ExerciseLocator.locate(2, 40000, "\\Total\\Q3", GRADES, Map.of(), ORDER));
    }

    @Test
    void closestSubGradeAboveOnThePage(){
        assertEquals("Q1", locate(2, 100000));
        assertEquals("Q2", locate(5, 100000)); // Under Q2 b, above Q3
        assertEquals("Q3", locate(5, 200000));
    }

    @Test
    void aCommentSlightlyAboveItsGradeLineBelongsToIt(){
        assertEquals("Q3", locate(5, 150000 - 2000));
    }

    @Test
    void firstSubGradeBelowWhenNothingAbove(){
        assertEquals("Q1", locate(2, 10000));
    }

    @Test
    void theTotalsTableIsNotUsedButLeafExercisesAre(){
        // Q1 and Q2 in the table of page 0 have sub-grades: only PNF is a leaf there.
        assertEquals("PNF", locate(0, 200000));
    }

    @Test
    void pageWithoutGradeUsesTheExercisePages(){
        assertEquals("Q2", ExerciseLocator.locate(4, 1000, null, GRADES, Map.of("Q1", 1, "Q2", 3), ORDER));
    }

    @Test
    void pageWithoutGradeNorPagesUsesThePreviousGrade(){
        assertEquals("Q2", locate(4, 1000)); // Q2 a on page 3
        assertEquals("Q3", locate(9, 1000));
    }

    @Test
    void noExerciseBeforeIsGeneral(){
        List<Grade> grades = List.of(new Grade("\\Total", 0, 0), new Grade("\\Total\\Q1\\a", 3, 1000));
        assertNull(ExerciseLocator.locate(1, 1000, null, grades, Map.of(), List.of("Q1")));
    }
}
