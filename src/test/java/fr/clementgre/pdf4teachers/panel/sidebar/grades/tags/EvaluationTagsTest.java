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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationTagsTest {

    private static final Placement AT = new Placement(1, 1000, 2000);

    @Test
    void tagsAreListedByExerciseMethodsFirst(){
        EvaluationTags tags = new EvaluationTags();
        Tag mistake = tags.create("Ex 1", "Forgot the factor", Kind.MISTAKE);
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag other = tags.create("Ex 2", "Formula", Kind.METHOD);
        assertEquals(List.of(chain, mistake), tags.getTags("Ex 1"));
        assertEquals(List.of(other), tags.getTags("Ex 2"));
    }

    @Test
    void creatingAnExistingNameReturnsTheSameTag(){
        EvaluationTags tags = new EvaluationTags();
        Tag tag = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        assertSame(tag, tags.create("Ex 1", "  chain   RULE ", Kind.MISTAKE));
        assertNotSame(tag, tags.create("Ex 2", "Chain rule", Kind.METHOD), "Another exercise has its own tags");
        assertThrows(IllegalArgumentException.class, () -> tags.create("Ex 1", "  ", Kind.METHOD));
    }

    @Test
    void togglesATagOnACopy(){
        EvaluationTags tags = new EvaluationTags();
        Tag tag = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        assertTrue(tags.toggle("01_A.pdf", tag, AT));
        assertTrue(tags.has("01_A.pdf", tag));
        assertEquals(AT, tags.getPlacement("01_A.pdf", tag));
        assertFalse(tags.toggle("01_A.pdf", tag, AT));
        assertFalse(tags.has("01_A.pdf", tag));
        assertTrue(tags.getCopies(tag).isEmpty());
    }

    @Test
    void listsTheCopiesOfATagAndThoseWithoutMethod(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag expand = tags.create("Ex 1", "Expand", Kind.METHOD);
        Tag mistake = tags.create("Ex 1", "Forgot the factor", Kind.MISTAKE);
        tags.add("02_B.pdf", chain, AT);
        tags.add("01_A.pdf", chain, AT);
        tags.add("03_C.pdf", expand, AT);
        tags.add("04_D.pdf", mistake, AT); // A mistake is not a method
        assertEquals(List.of("01_A.pdf", "02_B.pdf"), tags.getCopies(chain));
        assertEquals(List.of("04_D.pdf", "05_E.pdf"), tags.getWithoutMethod("Ex 1", List.of("01_A.pdf", "02_B.pdf", "03_C.pdf", "04_D.pdf", "05_E.pdf")));
    }

    @Test
    void renamingToAnotherTagNameIsRefused(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        tags.create("Ex 1", "Expand", Kind.METHOD);
        assertFalse(tags.rename(chain, "expand"));
        assertTrue(tags.rename(chain, "Chain rule (composition)"));
        assertEquals("Chain rule (composition)", chain.getName());
    }

    @Test
    void deletingATagRemovesItFromTheCopies(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        tags.add("01_A.pdf", chain, AT);
        tags.delete(chain);
        assertNull(tags.getTag(chain.getId()));
        assertTrue(tags.getTags("Ex 1").isEmpty());
        assertTrue(tags.toYAML().get("copies").toString().equals("{}"));
    }

    @Test
    void deletedCopiesAreForgotten(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        tags.add("01_A.pdf", chain, AT);
        tags.add("02_B.pdf", chain, AT);
        assertTrue(tags.retainCopies(Set.of("02_B.pdf")));
        assertEquals(List.of("02_B.pdf"), tags.getCopies(chain));
    }

    @Test
    void savedAndReadBack(){
        EvaluationTags tags = new EvaluationTags();
        Tag chain = tags.create("Ex 1", "Chain rule", Kind.METHOD);
        Tag mistake = tags.create("Ex 1", "Sign error", Kind.MISTAKE);
        tags.add("01_A.pdf", chain, AT);
        tags.add("01_A.pdf", mistake, new Placement(3, 5, 6));

        EvaluationTags read = EvaluationTags.fromYAML(tags.toYAML());
        Tag readChain = read.getTag(chain.getId());
        assertEquals("Chain rule", readChain.getName());
        assertEquals(Kind.METHOD, readChain.getKind());
        assertEquals(Kind.MISTAKE, read.getTag(mistake.getId()).getKind());
        assertEquals(AT, read.getPlacement("01_A.pdf", readChain));
        assertEquals(new Placement(3, 5, 6), read.getPlacement("01_A.pdf", read.getTag(mistake.getId())));
    }
}
