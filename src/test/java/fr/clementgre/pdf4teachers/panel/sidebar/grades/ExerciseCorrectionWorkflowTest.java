/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import org.junit.jupiter.api.Test;

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
}
