/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.lines.LineBank.Use;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LineBankTest {

    private static final DecimalFormat FORMAT = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.FRANCE));
    private static final List<String> LEAVES = List.of("a", "b");

    private static List<Use> uses(String... texts){
        return LineBank.collect(List.of(texts), LEAVES, "PNF", FORMAT);
    }

    private static List<String> texts(List<Use> uses){
        return uses.stream().map(Use::text).toList();
    }

    @Test
    void countsEachLineOncePerCopyTheMostUsedFirst(){
        List<Use> uses = uses("Correct\nNotation -0.5\n\nFaux", "Correct\n\nFaux\nCalcul cohérent 1", "Correct\nnotation - 0,5");
        Use correct = uses.getFirst();
        assertEquals("Correct", correct.text());
        assertEquals(0, correct.leaf());
        assertEquals(3, correct.copies());
        Use notation = uses.stream().filter(use -> use.key().equals("notation")).findFirst().orElseThrow();
        assertEquals(2, notation.copies()); // Written differently, same line
        assertEquals("Notation −0,5", notation.text());
        Use faux = uses.stream().filter(use -> use.key().equals("faux")).findFirst().orElseThrow();
        assertEquals(1, faux.leaf());
    }

    @Test
    void completesWhatIsTyped(){
        List<Use> uses = uses("Erreur de signe -0.5\n\nErreur formule -1", "Erreur de signe -0.5\n\nErreur formule -1", "\n\nErreur formule -1");
        // Same sub-question first, then the others
        assertEquals(List.of("Erreur de signe −0,5", "Erreur formule −1"), texts(LineBank.matches(uses, "err", 0, 6)));
        assertEquals(List.of("Erreur formule −1", "Erreur de signe −0,5"), texts(LineBank.matches(uses, "err", 1, 6)));
        assertEquals(List.of("Erreur formule −1"), texts(LineBank.matches(uses, "formule", 0, 6))); // A word anywhere
        assertEquals(1, LineBank.matches(uses, "e", 0, 1).size()); // From the first letter, at most the limit
        assertTrue(LineBank.matches(uses, "xyz", 0, 6).isEmpty());
        assertTrue(LineBank.matches(uses, "", 0, 6).isEmpty());
        assertTrue(LineBank.matches(uses, "Erreur formule", 1, 6).isEmpty()); // Typed in full: nothing to propose
    }

    @Test
    void sameWordsOtherPoints(){
        List<Use> uses = uses("Erreur formule -1", "Erreur formule -1", "Erreur formule -0.5");
        Use use = uses.getFirst();
        assertEquals(-1, use.points());
        assertEquals(2, use.copies()); // The copies giving these points, not the one giving −0.5
        LineGrading.Line typed = LineGrading.parseLine(0, "erreur formule -0.5", "PNF");
        assertEquals(-1, LineBank.findConflict(uses, typed, 0).points());
        assertNull(LineBank.findConflict(uses, LineGrading.parseLine(0, "Erreur formule -1", "PNF"), 0));
        assertNull(LineBank.findConflict(uses, typed, 1)); // Another sub-question
    }

    @Test
    void linesWithGAndPresentationMarksAreOfferedEverywhere(){
        // Copy 1: Ex 1 and Ex 2; copy 2: Ex 3. Only the lines with "g" and the PNF marks, once per copy
        List<List<LineGrading.Block>> copies = List.of(
                List.of(new LineGrading.Block(0, List.of(LineGrading.parseLine(0, "Notation -0.5 g", "PNF"),
                        LineGrading.parseLine(1, "Erreur -1", "PNF"))),
                        new LineGrading.Block(0, List.of(LineGrading.parseLine(0, "notation - 0,5 g", "PNF"),
                                LineGrading.parseLine(1, "PNF : unités", "PNF")))),
                List.of(new LineGrading.Block(1, List.of(LineGrading.parseLine(0, "Notation -0.5 g", "PNF")))));
        List<Use> everywhere = LineBank.collectEverywhere(copies, FORMAT);
        assertEquals(2, everywhere.size());
        Use notation = everywhere.getFirst();
        assertEquals("Notation −0,5 g", notation.text()); // Typed again with its "g"
        assertEquals(2, notation.copies());
        assertEquals(LineBank.EVERYWHERE, notation.leaf());
        assertEquals("PNF : unités", everywhere.get(1).text());
        // Tab completes with them too, after the lines of the sub-question
        assertEquals(List.of("Notation −0,5 g"), texts(LineBank.matches(everywhere, "nota", 0, 6)));
        assertEquals(List.of("PNF : unités"), texts(LineBank.matches(everywhere, "pnf", 0, 6))); // A presentation mark too
    }
}
