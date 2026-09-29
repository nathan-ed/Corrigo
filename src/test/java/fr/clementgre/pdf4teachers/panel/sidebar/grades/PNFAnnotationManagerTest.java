/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PNFAnnotationManagerTest {
    
    @Test
    void rowLabelsAreOneBased(){
        assertEquals("1. ", PNFAnnotationManager.getRowLabel(0));
        assertEquals("4. ", PNFAnnotationManager.getRowLabel(3));
    }
    
    @Test
    void firstPNFMarkKeepsRowLabel(){
        assertEquals("2. I", PNFAnnotationManager.addMarkToRowText("2. ", 1));
    }
    
    @Test
    void repeatedPNFMarksAreSpacedOnSameRow(){
        assertEquals("2. I  I", PNFAnnotationManager.addMarkToRowText("2. I", 1));
        assertEquals("2. I  I  I", PNFAnnotationManager.addMarkToRowText("2. I  I", 1));
    }
    
    @Test
    void pnfMarksAreCappedAtFourPerExercise(){
        assertEquals("2. I  I  I  I", PNFAnnotationManager.addMarkToRowText("2. I  I  I  I", 1));
        assertEquals(4, PNFAnnotationManager.countMarks("2. I  I  I  I"));
    }
    
    @Test
    void pnfRowDetectionAcceptsOnlyNumberedMarkRows(){
        assertTrue(PNFAnnotationManager.isPNFMarkRow("1. "));
        assertTrue(PNFAnnotationManager.isPNFMarkRow("1. I  I"));
        assertFalse(PNFAnnotationManager.isPNFMarkRow("PNF"));
        assertFalse(PNFAnnotationManager.isPNFMarkRow("1. x"));
        assertFalse(PNFAnnotationManager.isPNFMarkRow(""));
        assertFalse(PNFAnnotationManager.isPNFMarkRow("  "));
    }
    
    @Test
    void pnfTableAnchorMatchesRequestedGridCoordinates(){
        assertEquals(3199, PNFAnnotationManager.TABLE_X);
        assertEquals(130459, PNFAnnotationManager.TABLE_HEADER_Y);
    }
    
    @Test
    void pnfRowsArePlacedBelowHeader(){
        assertTrue(PNFAnnotationManager.getRowY(0) > PNFAnnotationManager.TABLE_HEADER_Y);
        assertTrue(PNFAnnotationManager.getRowY(1) > PNFAnnotationManager.getRowY(0));
    }
    
    @Test
    void rowsAreTheExercisesOtherThanPNF(){
        List<String> pnfFirst = List.of("PNF", "Q1", "Q2", "Q3", "Q4", "Q5");
        assertEquals(5, PNFAnnotationManager.getRowCount(pnfFirst));
        assertEquals(-1, PNFAnnotationManager.getRowIndex(pnfFirst, 0));
        assertEquals(0, PNFAnnotationManager.getRowIndex(pnfFirst, 1));
        assertEquals(4, PNFAnnotationManager.getRowIndex(pnfFirst, 5)); // The last exercise
        
        List<String> pnfLast = List.of("Ex 1", "Ex 2", "pnf ");
        assertEquals(2, PNFAnnotationManager.getRowCount(pnfLast));
        assertEquals(1, PNFAnnotationManager.getRowIndex(pnfLast, 1));
        assertEquals(-1, PNFAnnotationManager.getRowIndex(pnfLast, 2));
        
        List<String> noPNF = List.of("Q1", "Q2");
        assertEquals(2, PNFAnnotationManager.getRowCount(noPNF));
        assertEquals(1, PNFAnnotationManager.getRowIndex(noPNF, 1));
        assertEquals(-1, PNFAnnotationManager.getRowIndex(noPNF, 2));
        assertEquals(-1, PNFAnnotationManager.getRowIndex(noPNF, -1));
    }
}
