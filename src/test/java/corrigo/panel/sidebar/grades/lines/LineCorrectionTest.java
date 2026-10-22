/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// What is saved in the edit file of a copy and read back (no window needed)
class LineCorrectionTest {

    private static LinkedHashMap<String, Object> exercise(String text, List<String> ids, Map<String, Double> computed){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        data.put("ids", ids);
        data.put("computed", new LinkedHashMap<>(computed));
        return data;
    }

    @Test
    void savedAndReadBackUnchanged(){
        LinkedHashMap<String, Object> saved = new LinkedHashMap<>();
        saved.put("\\Total\\Ex 2", exercise("Correct\nNotation - 0.5\n\nFaux", List.of("a1", "a2", "", "a3"),
                Map.of("\\Total\\Ex 2\\a", 1.5, "\\Total\\Ex 2\\b", 0.0)));
        saved.put("\\Total\\Ex 1", exercise("Correct", List.of("b1"), Map.of("\\Total\\Ex 1", 3.0)));
        LineCorrection.load(saved);
        assertEquals(saved, LineCorrection.toYAML());
        // Read again from what was written: still the same
        LineCorrection.load(LineCorrection.toYAML());
        assertEquals(saved, LineCorrection.toYAML());
        assertEquals("Correct\nNotation - 0.5\n\nFaux", LineCorrection.getText("\\Total\\Ex 2"));
    }

    @Test
    void wrongIdsAreMadeAgainNotLost(){
        LinkedHashMap<String, Object> saved = new LinkedHashMap<>();
        saved.put("\\Total\\Ex 2", exercise("Correct\nFaux", List.of("only-one"), Map.of()));
        LineCorrection.load(saved);
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) ((Map<String, Object>) LineCorrection.toYAML().get("\\Total\\Ex 2")).get("ids");
        assertEquals(2, ids.size());
        assertEquals("Correct\nFaux", LineCorrection.getText("\\Total\\Ex 2"));
    }

    @Test
    void emptyOrBrokenDataIsIgnored(){
        LineCorrection.load(null);
        assertTrue(LineCorrection.isEmpty());
        LinkedHashMap<String, Object> broken = new LinkedHashMap<>();
        broken.put("\\Total\\Ex 2", "not a map");
        broken.put("\\Total\\Ex 3", exercise("", List.of(""), Map.of()));
        LineCorrection.load(broken);
        assertTrue(LineCorrection.isEmpty());
        assertTrue(LineCorrection.toYAML().isEmpty());
    }

    @Test
    void renamedGradesKeepTheirLines(){
        LinkedHashMap<String, Object> saved = new LinkedHashMap<>();
        saved.put("\\Total\\Ex 2", exercise("Correct\n\nFaux", List.of("a1", "", "a2"),
                Map.of("\\Total\\Ex 2\\a", 2.0, "\\Total\\Ex 2\\b", 0.0)));
        LineCorrection.load(saved);
        LineCorrection.onGradeRenamed("\\Total\\Ex 2\\b", "\\Total\\Ex 2\\b)");
        LineCorrection.onGradeRenamed("\\Total\\Ex 2", "\\Total\\Exercice 2");
        Map<String, Object> renamed = LineCorrection.toYAML();
        assertFalse(renamed.containsKey("\\Total\\Ex 2"));
        @SuppressWarnings("unchecked")
        Map<String, Object> exercise = (Map<String, Object>) renamed.get("\\Total\\Exercice 2");
        assertEquals("Correct\n\nFaux", exercise.get("text"));
        assertEquals(Map.of("\\Total\\Exercice 2\\a", 2.0, "\\Total\\Exercice 2\\b)", 0.0), exercise.get("computed"));
        // A path that only starts the same way is not renamed
        LineCorrection.onGradeRenamed("\\Total\\Exercice", "\\Total\\X");
        assertTrue(LineCorrection.toYAML().containsKey("\\Total\\Exercice 2"));
    }
}
