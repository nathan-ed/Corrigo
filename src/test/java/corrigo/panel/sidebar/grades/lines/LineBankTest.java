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
        assertEquals("Erreur de signe −0,5", LineBank.complete(uses, "err", 0).text()); // Same sub-question first
        assertEquals("Erreur formule −1", LineBank.complete(uses, "err", 1).text());
        assertEquals("Erreur formule −1", LineBank.complete(uses, "formule", 0).text()); // A word anywhere
        assertNull(LineBank.complete(uses, "e", 0)); // Too short
        assertNull(LineBank.complete(uses, "xyz", 0));
    }

    @Test
    void sameWordsOtherPoints(){
        List<Use> uses = uses("Erreur formule -1", "Erreur formule -1", "Erreur formule -0.5");
        Use use = uses.getFirst();
        assertEquals(-1, use.points());
        assertEquals(1, use.otherPoints().get(-.5));
        LineGrading.Line typed = LineGrading.parseLine(0, "erreur formule -0.5", "PNF");
        assertEquals(-1, LineBank.findConflict(uses, typed, 0).points());
        assertNull(LineBank.findConflict(uses, LineGrading.parseLine(0, "Erreur formule -1", "PNF"), 0));
        assertNull(LineBank.findConflict(uses, typed, 1)); // Another sub-question
    }
}
