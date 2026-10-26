/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GradingResetTest {

    // A graded copy: Total > Q1 (2 points) graded 1 with points, a comment, a mark and a quick mark
    private static Map<String, Object> gradedCopy(){
        LinkedHashMap<String, Object> base = new LinkedHashMap<>();
        ArrayList<Object> grades = new ArrayList<>();
        grades.add(grade("Total", "", 1, 2));
        LinkedHashMap<String, Object> q1 = grade("Q1", "\\Total", 1, 2);
        q1.put(ScoredCommentGrades.KEY_VALUE_SOURCE, ScoredCommentGrades.VALUE_SOURCE_COMMENTS);
        q1.put(ScoredCommentGrades.KEY_VALUE_BEFORE, -1d);
        q1.put(ScoredCommentGrades.KEY_START, 2d);
        q1.put(ScoredCommentGrades.KEY_MODE, "FULL");
        q1.put(ScoredCommentGrades.KEY_AUTO_MODE, "FULL");
        grades.add(q1);
        base.put("grades", grades);
        base.put("texts", new LinkedHashMap<>(Map.of("page0", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("text", "Sign error (-1)")))))));
        base.put("vectors", new LinkedHashMap<>(Map.of("page0", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("path", "M0 0L1 1")))))));
        base.put("images", new LinkedHashMap<String, Object>());
        base.put("skills", new LinkedHashMap<String, Object>());
        base.put("lastScrollValue", 0.4);
        return base;
    }
    private static LinkedHashMap<String, Object> grade(String name, String parentPath, double value, double total){
        LinkedHashMap<String, Object> grade = new LinkedHashMap<>();
        grade.put("page", 0);
        grade.put("parentPath", parentPath);
        grade.put("value", value);
        grade.put("total", total);
        grade.put("name", name);
        return grade;
    }

    @Test
    void restartKeepsTheGradeScaleWithoutGrades(){
        Map<String, Object> copy = gradedCopy();
        assertTrue(GradingReset.clear(copy, true));

        assertEquals(Map.of(), copy.get("texts"));
        assertEquals(Map.of(), copy.get("vectors"));
        assertFalse(copy.containsKey("lastScrollValue"));
        List<Map<String, Object>> grades = ScoredCommentGrades.getGrades(copy);
        assertEquals(List.of("\\Total", "\\Total\\Q1"), grades.stream().map(ScoredCommentGrades::getGradePath).toList());
        for(Map<String, Object> grade : grades){
            assertEquals(-1d, grade.get("value"));
            assertEquals(Set.of("page", "parentPath", "value", "total", "name"), grade.keySet());
        }
        assertEquals(2d, grades.get(1).get("total"));
    }

    @Test
    void resetScaleLeavesNothing(){
        Map<String, Object> copy = gradedCopy();
        assertFalse(GradingReset.clear(copy, false));
        assertTrue(ScoredCommentGrades.getGrades(copy).isEmpty());
        assertEquals(Map.of(), copy.get("texts"));
    }

    @Test
    void resetScaleKeepsTheSkills(){
        Map<String, Object> copy = gradedCopy();
        copy.put("skills", new LinkedHashMap<>(Map.of("assessmentId", 3)));
        assertTrue(GradingReset.clear(copy, false));
    }
}
