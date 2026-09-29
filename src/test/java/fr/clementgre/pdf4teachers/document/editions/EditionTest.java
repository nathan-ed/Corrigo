/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.document.editions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EditionTest {
    
    @Test
    @SuppressWarnings("unchecked")
    void preloadedEditionCopiesDoNotShareNestedData(){
        HashMap<String, Object> base = new HashMap<>();
        ArrayList<Object> grades = new ArrayList<>(List.of(new HashMap<>(Map.of("value", 1))));
        base.put("grades", grades);
        
        HashMap<String, Object> copy = Edition.deepCopy(base);
        ((Map<String, Object>) ((List<Object>) copy.get("grades")).getFirst()).put("value", 2);
        ((List<Object>) copy.get("grades")).add("added");
        
        assertEquals(1, ((Map<String, Object>) grades.getFirst()).get("value"));
        assertEquals(1, grades.size());
    }
}
