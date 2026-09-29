/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.datasaving.simpleconfigs;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.ExercisePageMapping;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ExerciseCorrectionDataTest {
    
    @Test
    void serializesExercisePageIndexes(){
        ExercisePageMapping mapping = new ExercisePageMapping();
        mapping.setPageIndex("Q1", 2);
        mapping.setPageIndex("Q3", 6);
        
        LinkedHashMap<String, Object> data = ExerciseCorrectionData.toConfigMap(mapping);
        
        assertEquals(2, data.get("Q1"));
        assertEquals(6, data.get("Q3"));
    }
    
    @Test
    void restoresExercisePageIndexes(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("Q2", 4);
        data.put("Q4", "9");
        
        ExercisePageMapping mapping = new ExercisePageMapping();
        ExerciseCorrectionData.applyPageIndexes(mapping, data);
        
        assertEquals(4, mapping.getPageIndex("Q2").orElseThrow());
        assertEquals(9, mapping.getPageIndex("Q4").orElseThrow());
    }
    
    @Test
    void ignoresMalformedPageIndexesWhenRestoring(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("Q2", "bad");
        
        ExercisePageMapping mapping = new ExercisePageMapping();
        ExerciseCorrectionData.applyPageIndexes(mapping, data);
        
        assertTrue(mapping.getPageIndex("Q2").isEmpty());
    }
}
