/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExercisePageMappingDialogTest {
    
    @Test
    void appliesOneBasedPageAttributions(){
        ExercisePageMapping mapping = new ExercisePageMapping();
        
        ExercisePageMappingDialog.applyValues(mapping, orderedValues("Q1", "1", "Q2", "3"), 5);
        
        assertEquals(0, mapping.getPageIndex("Q1").orElseThrow());
        assertEquals(2, mapping.getPageIndex("Q2").orElseThrow());
    }
    
    @Test
    void emptyValueClearsExistingAttribution(){
        ExercisePageMapping mapping = new ExercisePageMapping();
        mapping.setPageIndex("Q1", 2);
        
        ExercisePageMappingDialog.applyValues(mapping, orderedValues("Q1", ""), 5);
        
        assertTrue(mapping.getPageIndex("Q1").isEmpty());
    }
    
    @Test
    void rejectsNonNumericPageAttribution(){
        ExercisePageMapping mapping = new ExercisePageMapping();
        
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExercisePageMappingDialog.applyValues(mapping, orderedValues("Q2", "abc"), 5));
        
        assertEquals("Page for Q2 must be a number.", error.getMessage());
        assertTrue(mapping.getPageIndex("Q2").isEmpty());
    }
    
    @Test
    void rejectsOutOfBoundsPageAttributionWithoutPartialApply(){
        ExercisePageMapping mapping = new ExercisePageMapping();
        mapping.setPageIndex("Q1", 1);
        
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExercisePageMappingDialog.applyValues(mapping, orderedValues("Q1", "4", "Q2", "9"), 5));
        
        assertEquals("Page for Q2 must be between 1 and 5.", error.getMessage());
        assertEquals(1, mapping.getPageIndex("Q1").orElseThrow());
        assertTrue(mapping.getPageIndex("Q2").isEmpty());
    }
    
    private static Map<String, String> orderedValues(String... keysAndValues){
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for(int i = 0; i < keysAndValues.length; i += 2){
            values.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return values;
    }
}
