/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Use;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationTagsTest {

    private static final Placement AT = new Placement(1, 1000, 2000);
    private static final String GRADE = "\\Total\\Ex 1";

    @Test
    void tagsOfTheExerciseComeFirstThenTheOthers(){
        EvaluationTags tags = new EvaluationTags();
        Tag mistake = tags.create("Ex 1", "Forgot the factor", Kind.MISTAKE);
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag formula = tags.create("Ex 2", "Formula", Kind.METHOD);
        Tag sign = tags.create("Ex 3", "Sign error", Kind.MISTAKE);
        assertEquals(List.of(chain, mistake, formula, sign), tags.getTags("Ex 1"));
        assertEquals(List.of(formula, chain, mistake, sign), tags.getTags("Ex 2"));
        assertEquals(List.of(chain, mistake), tags.getExerciseTags("Ex 1"));
        // A tag used in an exercise is one of its tags
        tags.add("01_A.pdf", "Ex 2", sign, "", AT);
        assertEquals(List.of(formula, sign, chain, mistake), tags.getTags("Ex 2"));
        assertEquals(List.of(formula, sign), tags.getExerciseTags("Ex 2"));
    }

    @Test
    void creatingAnExistingNameReturnsTheSameTag(){
        EvaluationTags tags = new EvaluationTags();
        Tag tag = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        assertSame(tag, tags.create("Ex 1", "  chain   RULE ", Kind.MISTAKE));
        assertSame(tag, tags.create("Ex 2", "Chain rule", Kind.METHOD), "Tags are shared by the exercises");
        assertEquals("Ex 1", tag.getExercise());
        assertThrows(IllegalArgumentException.class, () -> tags.create("Ex 1", "  ", Kind.METHOD));
    }

    @Test
    void aTagCanBeOnACopySeveralTimesInAnExercise(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Ex 1", "Sign error", Kind.MISTAKE);
        Use first = tags.add("01_A.pdf", "Ex 1", sign, GRADE, AT);
        Use second = tags.add("01_A.pdf", "Ex 1", sign, GRADE, new Placement(1, 5000, 6000));
        tags.add("01_A.pdf", "Ex 2", sign, "\\Total\\Ex 2", AT);
        assertNotEquals(first.id(), second.id());
        assertEquals(2, tags.count("01_A.pdf", "Ex 1", sign));
        assertEquals(List.of(first, second), tags.getUses("01_A.pdf", "Ex 1", sign));
        assertEquals(1, tags.count("01_A.pdf", "Ex 2", sign));
        assertEquals(List.of("01_A.pdf"), tags.getCopies(sign), "Copies are counted once");
        assertEquals(Map.of("Ex 1", 1, "Ex 2", 1), tags.countByExercise(sign));

        assertEquals(first, tags.remove("01_A.pdf", first.id()));
        assertEquals(List.of(second), tags.getUses("01_A.pdf", "Ex 1", sign));
        tags.restore(first);
        assertEquals(2, tags.count("01_A.pdf", "Ex 1", sign));
        assertEquals(2, tags.removeAll("01_A.pdf", "Ex 1", sign).size());
        assertFalse(tags.has("01_A.pdf", "Ex 1", sign));
        assertTrue(tags.has("01_A.pdf", "Ex 2", sign));
    }

    @Test
    void anOccurrenceIsMovedOrChangedInPlace(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Ex 1", "Sign error", Kind.MISTAKE);
        Tag root = tags.create("Ex 1", "Wrong root", Kind.MISTAKE);
        Use use = tags.add("01_A.pdf", "Ex 1", sign, GRADE, AT);
        tags.move("01_A.pdf", use.id(), AT.withLabel(10, 20));
        tags.change("01_A.pdf", use.id(), root);
        Use changed = tags.getUse("01_A.pdf", use.id());
        assertEquals(root.getId(), changed.tag());
        assertEquals(GRADE, changed.grade());
        assertEquals(AT.withLabel(10, 20), changed.placement());
        assertFalse(tags.has("01_A.pdf", "Ex 1", sign));
    }

    @Test
    void pointsAreAddedByAMethodAndRemovedByAMistake(){
        EvaluationTags tags = new EvaluationTags();
        Tag sign = tags.create("Ex 1", "Sign error", Kind.MISTAKE);
        Tag nice = tags.create("Ex 1", "Nice method", Kind.METHOD);
        assertFalse(sign.isWrittenOnCopy());
        tags.setScoring(sign, -1.0, "  ");
        tags.setScoring(nice, 0.5, "Well done");
        assertEquals(1, sign.getPoints(), "Kept positive: the kind gives the sign");
        assertEquals(-1, sign.getSignedPoints());
        assertNull(sign.getComment());
        assertEquals(0.5, nice.getSignedPoints());
        assertTrue(sign.isWrittenOnCopy());
        tags.setKind(sign, Kind.METHOD);
        assertEquals(1, sign.getSignedPoints());
        tags.setScoring(nice, 0.0, null);
        assertFalse(nice.hasPoints());
        assertFalse(nice.isWrittenOnCopy());
        tags.setScoring(nice, null, "Only a comment");
        assertFalse(nice.hasPoints());
        assertTrue(nice.isWrittenOnCopy());
    }

    @Test
    void listsTheCopiesOfATagByExerciseAndThoseWithoutMethod(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag expand = tags.create("Ex 1", "Expand", Kind.METHOD);
        Tag mistake = tags.create("Ex 1", "Forgot the factor", Kind.MISTAKE);
        tags.add("02_B.pdf", "Ex 1", chain, "", AT);
        tags.add("01_A.pdf", "Ex 1", chain, "", AT);
        tags.add("03_C.pdf", "Ex 1", expand, "", AT);
        tags.add("04_D.pdf", "Ex 1", mistake, "", AT); // A mistake is not a method
        tags.add("05_E.pdf", "Ex 2", chain, "", AT); // A method in another exercise
        tags.add("01_A.pdf", "Ex 2", chain, "", AT);
        assertEquals(List.of("01_A.pdf", "02_B.pdf"), tags.getCopies("Ex 1", chain));
        assertEquals(List.of("01_A.pdf", "05_E.pdf"), tags.getCopies("Ex 2", chain));
        assertEquals(List.of("01_A.pdf", "02_B.pdf", "05_E.pdf"), tags.getCopies(chain));
        assertEquals(Map.of("Ex 1", 2, "Ex 2", 2), tags.countByExercise(chain));
        assertEquals(4, tags.getUses(chain).size());
        List<String> all = List.of("01_A.pdf", "02_B.pdf", "03_C.pdf", "04_D.pdf", "05_E.pdf");
        assertEquals(List.of("04_D.pdf", "05_E.pdf"), tags.getWithoutMethod("Ex 1", all));
        assertEquals(List.of("02_B.pdf", "03_C.pdf", "04_D.pdf"), tags.getWithoutMethod("Ex 2", all));
    }

    @Test
    void renamingToAnotherTagNameIsRefused(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        tags.create("Ex 2", "Expand", Kind.METHOD);
        assertFalse(tags.rename(chain, "expand"), "Even from another exercise");
        assertTrue(tags.rename(chain, "Chain rule (composition)"));
        assertEquals("Chain rule (composition)", chain.getName());
    }

    @Test
    void deletingATagRemovesItFromTheCopies(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        tags.add("01_A.pdf", "Ex 1", chain, "", AT);
        tags.add("01_A.pdf", "Ex 2", chain, "", AT);
        tags.delete(chain);
        assertNull(tags.getTag(chain.getId()));
        assertTrue(tags.getTags("Ex 1").isEmpty());
        assertEquals("{}", tags.toYAML().get("copies").toString());
    }

    @Test
    void deletedCopiesAreForgotten(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        tags.add("01_A.pdf", "Ex 1", chain, "", AT);
        tags.add("02_B.pdf", "Ex 1", chain, "", AT);
        assertTrue(tags.retainCopies(Set.of("02_B.pdf")));
        assertEquals(List.of("02_B.pdf"), tags.getCopies(chain));
    }

    @Test
    void savedAndReadBack(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag mistake = tags.create("Ex 1", "Sign error", Kind.MISTAKE);
        tags.setScoring(mistake, 1.0, "Check the sign");
        Use a = tags.add("01_A.pdf", "Ex 1", chain, GRADE, AT);
        Use b = tags.add("01_A.pdf", "Ex 1", mistake, GRADE, new Placement(3, 5, 6));
        Use c = tags.add("01_A.pdf", "Ex 1", mistake, GRADE, new Placement(4, 7, 8, 9, 10));

        EvaluationTags read = EvaluationTags.fromYAML(tags.toYAML());
        Tag readChain = read.getTag(chain.getId());
        Tag readMistake = read.getTag(mistake.getId());
        assertEquals("Chain rule", readChain.getName());
        assertEquals("Ex 1", readChain.getExercise());
        assertNull(readChain.getPoints());
        assertEquals(Kind.MISTAKE, readMistake.getKind());
        assertEquals(-1, readMistake.getSignedPoints());
        assertEquals("Check the sign", readMistake.getComment());
        assertEquals(a, read.getUse("01_A.pdf", a.id()));
        assertEquals(b, read.getUse("01_A.pdf", b.id()));
        assertEquals(c, read.getUse("01_A.pdf", c.id()));
        assertEquals(2, read.count("01_A.pdf", "Ex 1", readMistake));
    }

    @Test
    void oldTagsOfOneExerciseAreReadAndSameNamesMerged(){
        // Before tags were shared: uses had no exercise nor id, two exercises could have a tag with the same name
        Map<String, Object> data = Map.of(
                "tags", List.of(
                        Map.of("id", "a", "exercise", "Ex 1", "name", "Sign error", "kind", "MISTAKE"),
                        Map.of("id", "b", "exercise", "Ex 2", "name", "sign  error", "kind", "MISTAKE"),
                        Map.of("id", "c", "exercise", "Ex 2", "name", "Formula", "kind", "METHOD")),
                "copies", Map.of(
                        "01_A.pdf", List.of(Map.of("tag", "a", "page", 1, "x", 1000.0, "y", 2000.0),
                                Map.of("tag", "b", "page", 2, "x", 3.0, "y", 4.0)),
                        "02_B.pdf", List.of(Map.of("tag", "c", "page", 1, "x", 1000.0, "y", 2000.0))));
        EvaluationTags read = EvaluationTags.fromYAML(data);
        Tag sign = read.getTag("a");
        assertNull(read.getTag("b"));
        assertEquals(2, read.getAllTags().size());
        assertEquals(AT, read.getUses("01_A.pdf", "Ex 1", sign).getFirst().placement());
        assertEquals(new Placement(2, 3, 4), read.getUses("01_A.pdf", "Ex 2", sign).getFirst().placement());
        assertTrue(read.has("02_B.pdf", "Ex 2", read.getTag("c")));
    }

    @Test
    void theSpotOfTheExerciseIsNotASpotOfTheCopy(){
        assertTrue(Placement.exercise(2).isExerciseSpot());
        assertFalse(AT.isExerciseSpot());
        assertTrue(AT.isNear(new Placement(1, 1500, 2500)));
        assertFalse(AT.isNear(new Placement(2, 1000, 2000)));
        assertFalse(AT.isNear(new Placement(1, 90000, 2000)));
    }

    @Test
    void pointsCanBeTypedWithTheName(){
        assertEquals(new EvaluationTags.Typed("Sign error", 1.0, true), EvaluationTags.parseTyped(" Sign  error -1 "));
        assertEquals(new EvaluationTags.Typed("Sign error", 1.0, true), EvaluationTags.parseTyped("Sign error − 1"));
        assertEquals(new EvaluationTags.Typed("Nice method", 0.5, false), EvaluationTags.parseTyped("Nice method +0,5"));
        assertEquals(new EvaluationTags.Typed("Question 3", null, null), EvaluationTags.parseTyped("Question 3"), "No sign: part of the name");
        assertEquals(new EvaluationTags.Typed("Error", null, null), EvaluationTags.parseTyped("Error -0"));
        assertEquals(new EvaluationTags.Typed("-1", null, null), EvaluationTags.parseTyped("-1"), "A name is needed");
    }

    @Test
    void pointsBySubGradeAreSavedAndReadBack(){
        EvaluationTags tags = new EvaluationTags();
        Tag expand = tags.create("Q3", "Expanded", Kind.METHOD);
        tags.setGradePoints(expand, "Q3", Map.of("\\Total\\Q3\\a", -1.0, "\\Total\\Q3\\b", 0.0));
        assertEquals(Map.of("\\Total\\Q3\\a", 1.0), expand.getGradePoints(), "Positive, and 0 is none");
        assertTrue(expand.isWrittenOnCopy());
        Tag read = EvaluationTags.fromYAML(tags.toYAML()).getTag(expand.getId());
        assertEquals(Map.of("\\Total\\Q3\\a", 1.0), read.getGradePoints());
        assertTrue(read.getGradePoints("Q2").isEmpty());
    }
}
