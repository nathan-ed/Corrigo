/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.lines.LineGrading.Block;
import corrigo.panel.sidebar.grades.lines.LineGrading.Kind;
import corrigo.panel.sidebar.grades.lines.LineGrading.Line;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LineGradingTest {

    private static final DecimalFormat FORMAT = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.FRANCE));

    private static Line line(String text){
        return LineGrading.parseLine(0, text, "PNF");
    }
    private static Double grade(double total, String... lines){
        return LineGrading.grade(java.util.Arrays.stream(lines).map(LineGradingTest::line).toList(), total);
    }

    @Test
    void readsThePointsAsTheyAreTyped(){
        assertEquals(-.5, line("Notation - 0.5").points());
        assertEquals("Notation", line("Notation - 0.5").shown());
        assertEquals(1, line("Calculs cohérents 1").points());
        assertEquals(Kind.METHOD, line("Calculs cohérents 1").kind());
        assertEquals(1, line("Formule intégrale +1").points());
        assertEquals(-.5, line("Erreur formule −0,5").points());
        assertEquals(-.5, line("Erreur formule -0,5 pt").points());
        assertEquals(2, line("Calcul intégral +2pts").points());
        assertEquals(-.5, line("Erreur : -0.5").points());
        assertEquals("Erreur", line("Erreur : -0.5").shown());
        assertEquals(-1, line("-1").points());
        assertEquals("", line("-1").shown());
        assertEquals(-.5, line("Erreur de signe (−0,5)").points()); // As written on the copy
        assertEquals("Erreur de signe", line("Erreur de signe (−0,5)").shown());
    }

    @Test
    void aNumberOfTheTextIsNotPoints(){
        assertNull(line("x = 3").points());
        assertNull(line("2^3").points());
        assertNull(line("Q2").points());
        assertNull(line("Voir question 2").points());
        assertNull(line("cf. page 3").points());
        assertNull(line("u_n = -1").points());
        assertNull(line("Bien \"3\"").points());
        assertEquals(Kind.COMMENT, line("Voir question 2").kind());
        assertEquals("Voir question 2", line("Voir question 2").shown());
    }

    @Test
    void privateNotesAreNotWritten(){
        Line full = line("Correct (tous les points)");
        assertEquals(Kind.FULL, full.kind());
        assertEquals("Correct", full.shown());
        Line mistake = line("Erreur formule (vue en classe) -0.5");
        assertEquals(-.5, mistake.points());
        assertEquals("Erreur formule", mistake.shown());
        assertEquals(Kind.COMMENT, line("(rien à écrire)").kind());
        assertFalse(line("(rien à écrire)").isWritten());
    }

    @Test
    void keywords(){
        assertEquals(Kind.FULL, line("Correct").kind());
        assertEquals(Kind.FULL, line("cohérent").kind());
        assertEquals(Kind.FULL, line("Juste !").kind());
        assertEquals(Kind.ZERO, line("Faux").kind());
        assertEquals(Kind.ZERO, line("Pas de réponse").kind());
        assertEquals(Kind.ZERO, line("Réponse erronée").kind());
        assertEquals(Kind.COMMENT, line("Correct mais long").kind()); // A comment, not a keyword
        assertEquals(Kind.METHOD, line("Juste 1").kind()); // With points: a method
    }

    @Test
    void presentationMarks(){
        assertEquals(Kind.PNF, line("PNF").kind());
        assertEquals("", line("PNF").comment());
        assertEquals(Kind.PNF, line("PNF : unités manquantes").kind());
        assertEquals("unités manquantes", line("PNF : unités manquantes").comment());
        assertEquals("unités", line("pnf unités").comment());
        assertEquals(Kind.COMMENT, line("PNFX").kind());
        assertEquals(Kind.PNF, LineGrading.parseLine(0, "Présentation : soin", "Présentation").kind());
    }

    @Test
    void gradeOfASubQuestion(){
        assertEquals(1.5, grade(2, "Correct", "Notation - 0.5"));
        assertEquals(.5, grade(2, "Faux", "Calculs cohérents 1", "PNF", "Erreur - 0.5"));
        assertEquals(3, grade(4, "Formule intégrale +1", "Calcul intégral +2"));
        assertEquals(1.5, grade(2, "Erreur formule -0.5")); // A mistake first: from the maximum
        assertEquals(.5, grade(2, "Formule +1", "Erreur -0.5")); // A method first: from 0
        assertEquals(1.5, grade(2, "Erreur -0.5", "Correct")); // "Correct" decides, wherever it is
        assertEquals(.5, grade(2, "Erreur -0.5", "Faux", "Méthode +1"));
        assertEquals(0, grade(2, "Pas de réponse"));
        assertEquals(2, grade(2, "Cohérent", "Bonus +1")); // Never above the maximum
        assertEquals(0, grade(2, "Erreur -3")); // Never below 0
        assertEquals(0, grade(2, "Rien 0"));
        assertEquals(1, grade(2, "Attention 0", "Méthode +1")); // A point at 0 does not choose the start
        assertEquals(1, grade(2, "Erreur -0.5", "Erreur -0.5")); // Two equal lines: both count
        assertEquals(2, grade(2, "Correct", "Correct"));
        assertNull(grade(2, "Bien vu", "PNF")); // Only comments: not graded
        assertNull(grade(2));
    }

    @Test
    void blocksGoToTheSubQuestionsInOrder(){
        String text = "Correct\nNotation - 0.5\n\nFaux\nCalculs cohérents 1\nPNF\nErreur - 0.5\n";
        List<Block> blocks = LineGrading.parse(text, List.of("a", "b"), "PNF");
        assertEquals(2, blocks.size());
        assertEquals(0, blocks.get(0).leaf());
        assertEquals(1, blocks.get(1).leaf());
        assertEquals(1.5, LineGrading.grade(blocks.get(0).lines(), 2));
        assertEquals(.5, LineGrading.grade(blocks.get(1).lines(), 2));
        assertEquals(3, blocks.get(1).lines().get(0).index()); // Line numbers of the text (from 0)
    }

    @Test
    void severalBlankLinesAndExtraBlocks(){
        List<Block> blocks = LineGrading.parse("\n\nCorrect\n\n\n\nFaux\n\nEn trop +1", List.of("a", "b"), "PNF");
        assertEquals(3, blocks.size());
        assertEquals(0, blocks.get(0).leaf());
        assertEquals(1, blocks.get(1).leaf());
        assertEquals(-1, blocks.get(2).leaf()); // More blocks than sub-questions
    }

    @Test
    void labelsOfSubQuestionsNamedLikeInTheGradeScale(){
        List<String> named = List.of("a)", "b)");
        assertEquals(1, LineGrading.parse("b)\nCorrect", named, "PNF").getFirst().leaf());
        // A name without punctuation typed alone is a line, not a label
        List<Block> calcul = LineGrading.parse("Calcul\nCorrect", List.of("Limites", "Calcul"), "PNF");
        assertEquals(0, calcul.getFirst().leaf());
        assertEquals(2, calcul.getFirst().lines().size());
        List<String> numbered = List.of("1. Limites", "2. Aire");
        List<Block> blocks = LineGrading.parse("2.\nCorrect\n\n1. Limites\nFaux", numbered, "PNF");
        assertEquals(1, blocks.get(0).leaf());
        assertEquals(1, blocks.get(0).lines().size());
        assertEquals(0, blocks.get(1).leaf());
        assertEquals(Kind.ZERO, blocks.get(1).lines().getFirst().kind());
        List<String> words = List.of("Limites", "Mise en équation", "Calcul");
        assertEquals(1, LineGrading.parse("Mise en équation)\nCorrect", words, "PNF").getFirst().leaf());
        assertEquals("a)", LineGrading.labelOf("a)"));
        assertEquals("1.", LineGrading.labelOf("1. Limites"));
        assertEquals("Calcul)", LineGrading.labelOf("Calcul"));
        assertEquals("b)", LineGrading.labelOf("b"));
        // Each label found back
        for(List<String> names : List.of(named, numbered, words)){
            for(int i = 0; i < names.size(); i++){
                assertEquals(i, LineGrading.parse(LineGrading.labelOf(names.get(i)) + "\nCorrect", names, "PNF").getFirst().leaf(), names.get(i));
            }
        }
    }

    @Test
    void withoutSubQuestionsAllTheBlocksCount(){
        List<Block> blocks = LineGrading.parse("Correct\n\nErreur -0.5", List.of("Ex 1"), "PNF");
        assertEquals(2, blocks.size());
        assertTrue(blocks.stream().allMatch(block -> block.leaf() == 0));
    }

    @Test
    void aLabelChoosesTheSubQuestion(){
        List<Block> blocks = LineGrading.parse("b)\nCorrect\n\nc) Faux", List.of("a", "b", "c"), "PNF");
        assertEquals(1, blocks.get(0).leaf());
        assertEquals(Kind.FULL, blocks.get(0).lines().getFirst().kind());
        assertEquals(2, blocks.get(1).leaf());
        assertEquals(Kind.ZERO, blocks.get(1).lines().getFirst().kind());
        // Long names of sub-questions
        List<Block> named = LineGrading.parse("Question 2)\nCorrect", List.of("Question 1", "Question 2"), "PNF");
        assertEquals(1, named.getFirst().leaf());
        assertEquals(Kind.FULL, named.getFirst().lines().getFirst().kind());
        // Not a sub-question: a normal line
        List<Block> other = LineGrading.parse("x) Faux", List.of("a"), "PNF");
        assertEquals(0, other.getFirst().leaf());
        assertEquals(Kind.COMMENT, other.getFirst().lines().getFirst().kind());
    }

    @Test
    void textOnTheCopy(){
        assertEquals("Erreur formule (−0,5)", LineGrading.render(line("Erreur formule -0.5"), FORMAT, true));
        assertEquals("Erreur formule", LineGrading.render(line("Erreur formule -0.5"), FORMAT, false));
        assertEquals("Formule (+1)", LineGrading.render(line("Formule 1"), FORMAT, true));
        assertEquals("−1", LineGrading.render(line("-1"), FORMAT, false));
        assertEquals(LineGrading.CHECK + " Correct", LineGrading.render(line("Correct (tous les points)"), FORMAT, true));
        assertEquals(LineGrading.CROSS + " Faux", LineGrading.render(line("Faux"), FORMAT, true));
        assertEquals("Bien vu", LineGrading.render(line("Bien vu (copie propre)"), FORMAT, true));
    }

    @Test
    void ids(){
        int[] counter = {0};
        java.util.function.Supplier<String> next = () -> "n" + (++counter[0]);
        List<String> ids = LineIds.update(List.of(), List.of(), List.of("Correct", "Erreur -0.5", "", "Faux"), next);
        assertEquals(List.of("n1", "n2", "", "n3"), ids);
        // A line inserted at the start: the others keep their ids
        List<String> inserted = LineIds.update(List.of("Correct", "Erreur -0.5", "", "Faux"), ids,
                List.of("Formule +1", "Correct", "Erreur -0.5", "", "Faux"), next);
        assertEquals(List.of("n4", "n1", "n2", "", "n3"), inserted);
        // A line corrected (typo): same id
        List<String> corrected = LineIds.update(List.of("Correct", "Erreur -0.5", "", "Faux"), ids,
                List.of("Correct", "Erreur -1", "", "Faux"), next);
        assertEquals(List.of("n1", "n2", "", "n3"), corrected);
        // A line deleted: the others keep their ids, its id is gone
        List<String> deleted = LineIds.update(List.of("Correct", "Erreur -0.5", "", "Faux"), ids,
                List.of("Correct", "", "Faux"), next);
        assertEquals(List.of("n1", "", "n3"), deleted);
        // Two equal lines: each keeps its own id
        List<String> twice = LineIds.update(List.of("Erreur -0.5", "Erreur -0.5"), List.of("a", "b"),
                List.of("Erreur -0.5", "X", "Erreur -0.5"), next);
        assertEquals("a", twice.get(0));
        assertEquals("b", twice.get(2));
        // One of two equal lines deleted: the other keeps one of the ids, never a new one
        List<String> one = LineIds.update(List.of("Erreur -0.5", "Erreur -0.5"), List.of("a", "b"), List.of("Erreur -0.5"), next);
        assertTrue(one.equals(List.of("a")) || one.equals(List.of("b")));
    }

    // Random texts and edits (fixed seed): a grade stays between 0 and the maximum, reading never fails, the ids stay
    // one per line and unique, and a line keeps its id when another line is typed, deleted or when it is corrected
    @Test
    void randomTextsKeepTheRules(){
        String[] pieces = {"Correct", "Faux", "Cohérent", "Pas de réponse", "Erreur formule", "Notation", "PNF", "PNF : unités",
                "x = 3", "Voir question 2", "(privé)", "b)", "a)", "Q2", "-0.5", "- 0.5", "+1", "1", "2,5", "−0,5", "0", "(−0,5)",
                "-1 pt", "1000", "-1000", "é", "\"3\"", ":", "  ", "$x$", "))", "(("};
        java.util.Random random = new java.util.Random(7);
        for(int t = 0; t < 20000; t++){
            StringBuilder text = new StringBuilder();
            for(int i = random.nextInt(9); i > 0; i--){
                for(int w = random.nextInt(3); w > 0; w--) text.append(pieces[random.nextInt(pieces.length)]).append(random.nextBoolean() ? " " : "");
                text.append(random.nextInt(5) == 0 ? "\n\n" : "\n");
            }
            for(Block block : LineGrading.parse(text.toString(), random.nextBoolean() ? List.of("a", "b", "c") : List.of("Ex"), "PNF")){
                double total = random.nextInt(6);
                Double grade = LineGrading.grade(block.lines(), total);
                assertTrue(grade == null || grade >= 0 && grade <= total, text + " → " + grade);
            }
            List<String> lines = new java.util.ArrayList<>(List.of(text.toString().split("\\R", -1)));
            List<String> ids = LineIds.update(List.of(), List.of(), lines);
            int position = random.nextInt(lines.size() + 1);
            List<String> typed = new java.util.ArrayList<>(lines);
            typed.add(position, "Nouvelle ligne " + t);
            List<String> after = LineIds.update(lines, ids, typed);
            assertEquals(typed.size(), after.size());
            assertEquals(after.stream().filter(id -> !id.isEmpty()).count(), after.stream().filter(id -> !id.isEmpty()).distinct().count());
            for(int i = 0; i < lines.size(); i++){
                if(!lines.get(i).isBlank()) assertEquals(ids.get(i), after.get(i < position ? i : i + 1), lines + " + line at " + position);
            }
        }
    }
}
