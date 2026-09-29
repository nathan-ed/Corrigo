/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationTagsTest {

    private static final Placement AT = new Placement(1, 1000, 2000);

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
        tags.add("01_A.pdf", "Ex 2", sign, AT);
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
    void togglesATagOnACopyForAnExercise(){
        EvaluationTags tags = new EvaluationTags();
        Tag tag = tags.create("Ex 1", "Sign error", Kind.MISTAKE);
        assertTrue(tags.toggle("01_A.pdf", "Ex 1", tag, AT));
        assertTrue(tags.has("01_A.pdf", "Ex 1", tag));
        assertFalse(tags.has("01_A.pdf", "Ex 2", tag));
        assertEquals(AT, tags.getPlacement("01_A.pdf", "Ex 1", tag));
        assertTrue(tags.toggle("01_A.pdf", "Ex 2", tag, new Placement(2, 3, 4)));
        assertEquals(List.of("01_A.pdf"), tags.getCopies(tag));
        assertFalse(tags.toggle("01_A.pdf", "Ex 1", tag, AT));
        assertFalse(tags.has("01_A.pdf", "Ex 1", tag));
        assertTrue(tags.has("01_A.pdf", "Ex 2", tag));
        assertFalse(tags.toggle("01_A.pdf", "Ex 2", tag, AT));
        assertTrue(tags.getCopies(tag).isEmpty());
    }

    @Test
    void listsTheCopiesOfATagByExerciseAndThoseWithoutMethod(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag expand = tags.create("Ex 1", "Expand", Kind.METHOD);
        Tag mistake = tags.create("Ex 1", "Forgot the factor", Kind.MISTAKE);
        tags.add("02_B.pdf", "Ex 1", chain, AT);
        tags.add("01_A.pdf", "Ex 1", chain, AT);
        tags.add("03_C.pdf", "Ex 1", expand, AT);
        tags.add("04_D.pdf", "Ex 1", mistake, AT); // A mistake is not a method
        tags.add("05_E.pdf", "Ex 2", chain, AT); // A method in another exercise
        tags.add("01_A.pdf", "Ex 2", chain, AT);
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
        tags.add("01_A.pdf", "Ex 1", chain, AT);
        tags.add("01_A.pdf", "Ex 2", chain, AT);
        tags.delete(chain);
        assertNull(tags.getTag(chain.getId()));
        assertTrue(tags.getTags("Ex 1").isEmpty());
        assertEquals("{}", tags.toYAML().get("copies").toString());
    }

    @Test
    void deletedCopiesAreForgotten(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        tags.add("01_A.pdf", "Ex 1", chain, AT);
        tags.add("02_B.pdf", "Ex 1", chain, AT);
        assertTrue(tags.retainCopies(Set.of("02_B.pdf")));
        assertEquals(List.of("02_B.pdf"), tags.getCopies(chain));
    }

    @Test
    void savedAndReadBack(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag mistake = tags.create("Ex 1", "Sign error", Kind.MISTAKE);
        tags.add("01_A.pdf", "Ex 1", chain, AT);
        tags.add("01_A.pdf", "Ex 1", mistake, new Placement(3, 5, 6));
        tags.add("01_A.pdf", "Ex 2", mistake, new Placement(4, 7, 8));

        EvaluationTags read = EvaluationTags.fromYAML(tags.toYAML());
        Tag readChain = read.getTag(chain.getId());
        Tag readMistake = read.getTag(mistake.getId());
        assertEquals("Chain rule", readChain.getName());
        assertEquals("Ex 1", readChain.getExercise());
        assertEquals(Kind.METHOD, readChain.getKind());
        assertEquals(Kind.MISTAKE, readMistake.getKind());
        assertEquals(AT, read.getPlacement("01_A.pdf", "Ex 1", readChain));
        assertEquals(new Placement(3, 5, 6), read.getPlacement("01_A.pdf", "Ex 1", readMistake));
        assertEquals(new Placement(4, 7, 8), read.getPlacement("01_A.pdf", "Ex 2", readMistake));
    }

    @Test
    void oldTagsOfOneExerciseAreReadAndSameNamesMerged(){
        // Before tags were shared: uses had no exercise, two exercises could have a tag with the same name
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
        assertEquals(AT, read.getPlacement("01_A.pdf", "Ex 1", sign));
        assertEquals(new Placement(2, 3, 4), read.getPlacement("01_A.pdf", "Ex 2", sign));
        assertTrue(read.has("02_B.pdf", "Ex 2", read.getTag("c")));
    }
}
