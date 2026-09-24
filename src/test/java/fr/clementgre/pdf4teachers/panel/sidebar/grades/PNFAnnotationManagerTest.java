/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import org.junit.jupiter.api.Test;

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
}
