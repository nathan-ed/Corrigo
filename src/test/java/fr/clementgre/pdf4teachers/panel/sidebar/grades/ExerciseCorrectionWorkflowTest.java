/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class ExerciseCorrectionWorkflowTest {
    
    @Test
    void navigationTargetIsEmptyWhenModeIsOff(){
        OptionalInt target = ExerciseCorrectionWorkflow.getNavigationTarget(false, OptionalInt.of(3), 10);
        
        assertTrue(target.isEmpty());
    }
    
    @Test
    void navigationTargetIsEmptyWithoutMappedExercise(){
        OptionalInt target = ExerciseCorrectionWorkflow.getNavigationTarget(true, OptionalInt.empty(), 10);
        
        assertTrue(target.isEmpty());
    }
    
    @Test
    void navigationTargetClampsToDocumentPageCount(){
        OptionalInt target = ExerciseCorrectionWorkflow.getNavigationTarget(true, OptionalInt.of(12), 8);
        
        assertTrue(target.isPresent());
        assertEquals(7, target.getAsInt());
    }
    
    @Test
    void navigationTargetRejectsEmptyDocuments(){
        OptionalInt target = ExerciseCorrectionWorkflow.getNavigationTarget(true, OptionalInt.of(0), 0);
        
        assertTrue(target.isEmpty());
    }
    
    @Test
    void prefetchRangeIncludesTargetAndNextPage(){
        assertEquals(4, ExerciseCorrectionWorkflow.getPrefetchLastPage(3, 10));
    }
    
    @Test
    void prefetchRangeClampsOnLastPage(){
        assertEquals(9, ExerciseCorrectionWorkflow.getPrefetchLastPage(9, 10));
    }
    
    @Test
    void prefetchRangeRejectsInvalidInput(){
        assertThrows(IllegalArgumentException.class, () -> ExerciseCorrectionWorkflow.getPrefetchLastPage(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> ExerciseCorrectionWorkflow.getPrefetchLastPage(0, 0));
    }
    
    @Test
    void newTopLevelGradeStaysOnFirstPage(){
        assertEquals(0, ExerciseCorrectionWorkflow.getNewGradePageIndex(true, OptionalInt.of(4), 3, 8));
    }
    
    @Test
    void newSubGradeUsesMappedExercisePage(){
        assertEquals(4, ExerciseCorrectionWorkflow.getNewGradePageIndex(false, OptionalInt.of(4), 0, 8));
    }
    
    @Test
    void newSubGradeFallsBackToCurrentPageWhenUnmapped(){
        assertEquals(2, ExerciseCorrectionWorkflow.getNewGradePageIndex(false, OptionalInt.empty(), 2, 8));
    }
    
    @Test
    void newGradePageIndexClampsToDocumentBounds(){
        assertEquals(7, ExerciseCorrectionWorkflow.getNewGradePageIndex(false, OptionalInt.of(20), 2, 8));
        assertEquals(0, ExerciseCorrectionWorkflow.getNewGradePageIndex(false, OptionalInt.empty(), -2, 8));
    }
    
    @Test
    void newGradePageIndexRejectsEmptyDocuments(){
        assertThrows(IllegalArgumentException.class, () -> ExerciseCorrectionWorkflow.getNewGradePageIndex(true, OptionalInt.empty(), 0, 0));
    }
    
    @Test
    void generatedSummaryRowsStayOnFirstPage(){
        assertEquals(0, ExerciseCorrectionWorkflow.getGeneratedGradePageIndex(true, OptionalInt.of(5), 3, 8));
    }
    
    @Test
    void generatedSubQuestionUsesMappedExercisePage(){
        assertEquals(5, ExerciseCorrectionWorkflow.getGeneratedGradePageIndex(false, OptionalInt.of(5), 1, 8));
    }
    
    @Test
    void generatedSubQuestionFallsBackToExistingPageWhenUnmapped(){
        assertEquals(3, ExerciseCorrectionWorkflow.getGeneratedGradePageIndex(false, OptionalInt.empty(), 3, 8));
    }
    
    @Test
    void generatedGradePageIndexClampsToDocumentBounds(){
        assertEquals(7, ExerciseCorrectionWorkflow.getGeneratedGradePageIndex(false, OptionalInt.of(12), 3, 8));
        assertEquals(0, ExerciseCorrectionWorkflow.getGeneratedGradePageIndex(false, OptionalInt.empty(), -1, 8));
    }
    
    private static Map<String, Double> grades(Object... pathsAndValues){
        LinkedHashMap<String, Double> grades = new LinkedHashMap<>();
        for(int i = 0; i < pathsAndValues.length; i += 2) grades.put((String) pathsAndValues[i], (Double) pathsAndValues[i + 1]);
        return grades;
    }
    
    @Test
    void exerciseIsGradedWhenAllSubGradesHaveAValue(){
        Map<String, Double> copy = grades("\\Total", 5d, "Total\\Ex 1", 5d, "Total\\Ex 1\\a", 2d, "Total\\Ex 1\\b", 3d, "Total\\Ex 2", -1d, "Total\\Ex 2\\a", -1d);
        
        assertTrue(ExerciseCorrectionWorkflow.isExerciseGraded(copy, "Total\\Ex 1"));
        assertFalse(ExerciseCorrectionWorkflow.isExerciseGraded(copy, "Total\\Ex 2"));
    }
    
    @Test
    void exerciseIsNotGradedWhenOneSubGradeIsMissing(){
        Map<String, Double> copy = grades("Total\\Ex 1", 2d, "Total\\Ex 1\\a", 2d, "Total\\Ex 1\\b", -1d);
        
        assertFalse(ExerciseCorrectionWorkflow.isExerciseGraded(copy, "Total\\Ex 1"));
    }
    
    @Test
    void exerciseWithoutSubGradeUsesItsOwnValue(){
        assertTrue(ExerciseCorrectionWorkflow.isExerciseGraded(grades("Total\\Ex 1", 4d), "Total\\Ex 1"));
        assertFalse(ExerciseCorrectionWorkflow.isExerciseGraded(grades("Total\\Ex 1", -1d), "Total\\Ex 1"));
    }
    
    @Test
    void exerciseIsNotGradedInACopyWithoutIt(){
        assertFalse(ExerciseCorrectionWorkflow.isExerciseGraded(grades(), "Total\\Ex 1"));
        assertFalse(ExerciseCorrectionWorkflow.isExerciseGraded(grades("Total\\Ex 10", 4d), "Total\\Ex 1"));
    }
    
    @Test
    void neighborFileSkipsNonMatchingFilesAndWraps(){
        boolean[] ungraded = {true, false, false, false, true};
        
        assertEquals(OptionalInt.of(4), ExerciseCorrectionWorkflow.findNeighborFile(5, 1, 1, i -> ungraded[i]));
        assertEquals(OptionalInt.of(0), ExerciseCorrectionWorkflow.findNeighborFile(5, 4, 1, i -> ungraded[i]));
        assertEquals(OptionalInt.of(0), ExerciseCorrectionWorkflow.findNeighborFile(5, 2, -1, i -> ungraded[i]));
        assertEquals(OptionalInt.of(4), ExerciseCorrectionWorkflow.findNeighborFile(5, 0, -1, i -> ungraded[i]));
    }
    
    @Test
    void neighborFileNeverReturnsTheCurrentFile(){
        assertTrue(ExerciseCorrectionWorkflow.findNeighborFile(3, 1, 1, i -> i == 1).isEmpty());
        assertTrue(ExerciseCorrectionWorkflow.findNeighborFile(1, 0, 1, i -> true).isEmpty());
        assertTrue(ExerciseCorrectionWorkflow.findNeighborFile(0, 0, 1, i -> true).isEmpty());
    }
}
