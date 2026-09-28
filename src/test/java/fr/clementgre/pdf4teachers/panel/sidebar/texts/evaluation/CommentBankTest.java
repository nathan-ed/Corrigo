/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CommentBankTest {

    private static final CommentBank.Style RED = new CommentBank.Style("Open Sans", 16, false, false, "0xff0000ff", 90);

    private static CommentBank.Occurrence occ(String text, String exercise){
        return new CommentBank.Occurrence(text, 0, 0, 0, exercise, RED);
    }
    private static CommentBank.Entry find(CommentBank bank, String text, String exercise){
        return bank.getEntries().stream().filter(e -> e.getText().equals(text) && Objects.equals(e.getFoundExercise(), exercise)).findFirst().orElse(null);
    }

    @Test
    void countsTheCopiesUsingEachComment(){
        CommentBank bank = new CommentBank();
        assertTrue(bank.updateCopy("1_A.pdf", List.of(occ("pas justifié", "Q1"), occ("PNF", null)), 1));
        bank.updateCopy("2_B.pdf", List.of(occ("pas justifié", "Q1")), 2);
        assertEquals(2, find(bank, "pas justifié", "Q1").getUses());
        assertEquals(1, find(bank, "PNF", null).getUses());
    }

    @Test
    void sameTextInTwoExercisesIsListedInBoth(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(occ("Justifier !", "Q1"), occ("Justifier !", "Q3")), 1);
        assertNotNull(find(bank, "Justifier !", "Q1"));
        assertNotNull(find(bank, "Justifier !", "Q3"));
    }

    @Test
    void updatingACopyReplacesItsContributionButKeepsTheEntry(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(occ("faux", "Q2")), 1);
        assertTrue(bank.updateCopy("1_A.pdf", List.of(), 2));
        CommentBank.Entry entry = find(bank, "faux", "Q2");
        assertNotNull(entry, "A comment removed from all the copies can still be reused");
        assertEquals(0, entry.getUses());
        assertFalse(bank.updateCopy("1_A.pdf", List.of(), 3), "Nothing changed");
    }

    @Test
    void blankCommentsAreIgnoredAndTextsAreTrimmed(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(occ("  ", "Q1"), occ(" bien \n", "Q1")), 1);
        assertEquals(1, bank.getEntries().size());
        assertEquals("bien", bank.getEntries().iterator().next().getText());
    }

    @Test
    void commentsDifferingOnlyByLineBreaksAreTheSame(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(occ("Apprendre les démonstrations\ndu cours!", "PNF")), 1);
        bank.updateCopy("2_B.pdf", List.of(occ("Apprendre les  démonstrations du cours!", "PNF")), 1);
        assertEquals(1, bank.getEntries().size());
        assertEquals(2, bank.getEntries().iterator().next().getUses());
    }
    
    @Test
    void remembersTheFirstPageOfACommentInEachCopy(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(new CommentBank.Occurrence("faux", 5, 0, 0, "Q2", RED), new CommentBank.Occurrence("faux", 3, 0, 0, "Q2", RED)), 1);
        CommentBank.Entry entry = find(bank, "faux", "Q2");
        assertEquals(1, entry.getUses());
        assertEquals(3, entry.getPage("1_A.pdf"));
        assertEquals(3, find(CommentBank.fromYAML(bank.toYAML()), "faux", "Q2").getPage("1_A.pdf"));
        assertEquals(-1, entry.getPage("2_B.pdf"));
    }
    
    @Test
    void deletedCopiesNoLongerCount(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(occ("faux", "Q2")), 1);
        bank.updateCopy("2_B.pdf", List.of(occ("faux", "Q2")), 1);
        assertTrue(bank.retainCopies(Set.of("2_B.pdf")));
        assertEquals(Set.of("2_B.pdf"), find(bank, "faux", "Q2").getCopies());
    }

    @Test
    void movedAndHiddenEntriesSurviveRescansAndSaving(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(occ("Apprendre le cours", "PNF"), occ("ok", "Q1")), 1);
        bank.move(find(bank, "Apprendre le cours", "PNF"), null);
        bank.hide(find(bank, "ok", "Q1"));
        bank.updateCopy("1_A.pdf", List.of(occ("Apprendre le cours", "PNF"), occ("ok", "Q1")), 2);

        CommentBank read = CommentBank.fromYAML(bank.toYAML());
        CommentBank.Entry moved = find(read, "Apprendre le cours", "PNF");
        assertNull(moved.getExercise(), "Moved to general");
        assertEquals(Set.of("1_A.pdf"), moved.getCopies());
        assertEquals(RED, moved.getStyle());
        assertTrue(find(read, "ok", "Q1").isHidden());
        assertEquals(List.of(moved), read.getVisibleEntries());
    }

    @Test
    void movingBackToTheFoundExerciseClearsTheMove(){
        CommentBank bank = new CommentBank();
        bank.updateCopy("1_A.pdf", List.of(occ("x", "Q1")), 1);
        CommentBank.Entry entry = find(bank, "x", "Q1");
        bank.move(entry, "Q2");
        assertEquals("Q2", entry.getExercise());
        bank.move(entry, "Q1");
        assertEquals("Q1", entry.getExercise());
        assertFalse(CommentBank.fromYAML(bank.toYAML()).toYAML().toString().contains("movedTo"));
    }

    @Test
    void readsCommentsOfAnEditFile(){
        Map<String, Object> base = new HashMap<>();
        base.put("grades", List.of(
                grade("", "Total", 0, 180000),
                grade("\\Total", "Q1", 0, 140000),
                grade("\\Total\\Q1", "a", 1, 50000),
                grade("\\Total", "Q2", 0, 150000),
                grade("\\Total\\Q2", "a", 2, 50000)));
        LinkedHashMap<String, Object> texts = new LinkedHashMap<>();
        texts.put("page1", List.of(text("pas justifié", 60000, Map.of())));
        texts.put("page2", List.of(
                text("faux", 60000, Map.of()),
                text("−0.5 signe", 70000, Map.of("scoredCommentId", "abc")), // Scored comment: listed in the grading panel
                text("4.5", 80000, Map.of("mark", true)), // Computed mark
                text("voir Q1", 90000, Map.of("gradeComment", "\\Total\\Q1\\a"))));
        base.put("texts", texts);

        List<CommentBank.Occurrence> read = CopyComments.read(base, Map.of(), List.of("Q1", "Q2"));
        assertEquals(List.of("pas justifié|Q1", "faux|Q2", "voir Q1|Q1"),
                read.stream().map(o -> o.text() + "|" + o.exercise()).toList());
        assertEquals(RED, read.getFirst().style());
    }
    private static Map<String, Object> grade(String parentPath, String name, int page, int y){
        return new HashMap<>(Map.of("parentPath", parentPath, "name", name, "page", page, "y", y));
    }
    private static Map<String, Object> text(String text, int y, Map<String, Object> extra){
        HashMap<String, Object> data = new HashMap<>(Map.of("text", text, "x", 1000, "y", y, "font", "Open Sans", "size", 16.0,
                "bold", false, "italic", false, "color", "0xff0000ff", "maxWidth", 90.0));
        data.putAll(extra);
        return data;
    }
}
