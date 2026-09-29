/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MarksTest {

    @Test
    void roundsToTheNearestHalf(){
        assertEquals(6, Marks.roundToHalf(5.75));
        assertEquals(5.5, Marks.roundToHalf(5.749));
        assertEquals(5.5, Marks.roundToHalf(5.25));
        assertEquals(5, Marks.roundToHalf(5.249));
        assertEquals(4, Marks.roundToHalf(4));
    }

    @Test
    void computesFromObtainedAndTotal(){
        assertEquals(6, Marks.compute(20, 20));
        assertEquals(1, Marks.compute(0, 20));
        assertEquals(4.5, Marks.compute(14, 20)); // 4.5
        assertEquals(6, Marks.compute(19, 20)); // 5.75
        assertEquals(5.5, Marks.compute(17.5, 20)); // 5.375: must not become 5 with floating point errors
        assertEquals(4, Marks.compute(11, 20)); // 3.75
        assertEquals(3.5, Marks.compute(10.9, 20)); // 3.725
    }

    @Test
    void markStaysBetweenOneAndSix(){
        assertEquals(6, Marks.compute(23, 20)); // Bonus points
        assertEquals(1, Marks.compute(5, 0));
    }

    @Test
    void findsTheRaiseThatChangesTheMark(){
        // 16.5/20 → 5.125 → 5 ; 17/20 → 5.25 → 5.5
        assertEquals(OptionalDouble.of(.5), Marks.getChangingRaise(16.5, 20));
        // 16/20 → 5 ; 16.5 → 5 ; 17 → 5.5
        assertEquals(OptionalDouble.of(1), Marks.getChangingRaise(16, 20));
        // 15/20 → 4.75 → 5 ; 16 → 5
        assertEquals(OptionalDouble.empty(), Marks.getChangingRaise(15, 20));
    }

    @Test
    void noRaiseAboveTheTotal(){
        assertEquals(OptionalDouble.empty(), Marks.getChangingRaise(20, 20));
        // 19.5/20 → 5.875 → 6 already
        assertEquals(OptionalDouble.empty(), Marks.getChangingRaise(19.5, 20));
        // 18.5/40 → 3.3125 → 3.5 ; 19 → 3.375 → 3.5 ; 19.5 → 3.4375 → 3.5
        assertEquals(OptionalDouble.empty(), Marks.getChangingRaise(18.5, 40));
        // 3/4 → 4.75 → 5 ; 3.5/4 → 5.375 → 5.5 ; +1 is above the total
        assertEquals(OptionalDouble.of(.5), Marks.getChangingRaise(3, 4));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradedWhenAllLeavesHaveAValue(){
        Map<String, Object> base = editBase(
                grade("", "Total", 12),
                grade("\\Total", "Ex 1", 7),
                grade("\\Total\\Ex 1", "a", 3),
                grade("\\Total\\Ex 1", "b", 4),
                grade("\\Total", "Ex 2", 5),
                grade("\\Total", "Bonus", -1));
        assertTrue(Marks.isGraded(base, name -> name.equals("Bonus")));

        ((List<Map<String, Object>>) base.get("grades")).get(2).put("value", -1d);
        assertFalse(Marks.isGraded(base, name -> name.equals("Bonus")));

        assertFalse(Marks.isGraded(editBase(), name -> false));
    }

    @Test
    void readsTheValueOfAMarkText(){
        assertEquals(OptionalDouble.of(4.5), Marks.parseMarkText("4.5"));
        assertEquals(OptionalDouble.of(4.5), Marks.parseMarkText("4,5"));
        assertEquals(OptionalDouble.of(6), Marks.parseMarkText(" 6 "));
        assertEquals(OptionalDouble.of(5.5), Marks.parseMarkText("Mark: 5.5"));
        assertEquals(OptionalDouble.of(4.5), Marks.parseMarkText("Note : 4,5"));
        assertEquals(OptionalDouble.empty(), Marks.parseMarkText("Mark: –"));
        assertEquals(OptionalDouble.empty(), Marks.parseMarkText(null));
    }
    
    @Test
    void positionRoundTripsThroughYAML(){
        Marks.Position position = new Marks.Position(1, 12000, 3400);
        assertEquals(position, Marks.Position.fromYAML(position.toYAML()));
        assertNull(Marks.Position.fromYAML(Map.of("page", 0)));
        assertNull(Marks.Position.fromYAML(null));
    }
    
    @Test
    void replacesTheMarkText(){
        Map<String, Object> base = editBase();
        LinkedHashMap<String, Object> texts = new LinkedHashMap<>();
        texts.put("page0", new ArrayList<>(List.of(text("Hello"), markText("Mark: 4"))));
        base.put("texts", texts);

        Marks.setMarkText(base, 1, new LinkedHashMap<>(Map.of("text", "Mark: 5")));

        assertEquals(1, ((List<?>) texts.get("page0")).size());
        List<?> page1 = (List<?>) texts.get("page1");
        assertEquals(1, page1.size());
        assertEquals("Mark: 5", ((Map<?, ?>) page1.getFirst()).get("text"));
        assertEquals(true, ((Map<?, ?>) page1.getFirst()).get(TextElement.KEY_MARK));

        assertEquals(1, Marks.removeMarkTexts(base));
        assertEquals(0, Marks.removeMarkTexts(base));
    }

    @SafeVarargs
    private static Map<String, Object> editBase(Map<String, Object>... grades){
        LinkedHashMap<String, Object> base = new LinkedHashMap<>();
        base.put("grades", new ArrayList<>(List.of(grades)));
        return base;
    }
    private static Map<String, Object> grade(String parentPath, String name, double value){
        LinkedHashMap<String, Object> grade = new LinkedHashMap<>();
        grade.put("parentPath", parentPath);
        grade.put("name", name);
        grade.put("value", value);
        return grade;
    }
    private static Map<String, Object> text(String text){
        return new LinkedHashMap<>(Map.of("text", text));
    }
    private static Map<String, Object> markText(String text){
        return new LinkedHashMap<>(Map.of("text", text, TextElement.KEY_MARK, true));
    }
}
