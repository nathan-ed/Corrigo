/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import corrigo.panel.sidebar.grades.MarkScale.Kind;
import corrigo.panel.sidebar.grades.MarkScale.Threshold;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkScaleTest {

    @Test
    void theSwissScaleIsTheDefault(){
        assertEquals(6, MarkScale.SWISS.compute(20, 20));
        assertEquals(1, MarkScale.SWISS.compute(0, 20));
        assertEquals(5.5, MarkScale.SWISS.compute(17.5, 20), "5.375 → 5.5");
        assertEquals(4, MarkScale.SWISS.compute(12, 20));
        assertEquals(1, MarkScale.SWISS.compute(5, 0));
    }

    @Test
    void aLinearScaleWithOtherLimitsAndRounding(){
        MarkScale french = new MarkScale(Kind.LINEAR, 0, 20, .25, List.of(), "");
        assertEquals(13.25, french.compute(13.3, 20));
        MarkScale tenths = new MarkScale(Kind.LINEAR, 1, 6, .1, List.of(), "");
        assertEquals(4.8, tenths.compute(15.2, 20), 1e-9);
    }

    @Test
    void aTableGivesTheMarkOfTheHighestReachedThreshold(){
        MarkScale table = new MarkScale(Kind.TABLE, 1, 6, .5,
                List.of(new Threshold(18, 6), new Threshold(10, 4), new Threshold(14, 5)), "");
        assertEquals(6, table.compute(19, 20));
        assertEquals(5, table.compute(14, 20));
        assertEquals(4, table.compute(13.5, 20));
        assertEquals(1, table.compute(9, 20), "Below the table: the minimum");
    }

    @Test
    void aFormulaOfPointsAndTotal(){
        MarkScale formula = new MarkScale(Kind.FORMULA, 1, 6, .5, List.of(), "(p + 2) / t * 5 + 1");
        assertNull(formula.getError());
        assertEquals(5.5, formula.compute(16, 20), "18/20*5+1 = 5.5");
        assertEquals(6, formula.compute(20, 20), "Kept at the maximum");
        MarkScale functions = new MarkScale(Kind.FORMULA, 0, 10, 0, List.of(), "min(points, total) / total * 10 - abs(-1) + 2^2 - round(3.6)");
        assertEquals(5 - 1 + 4 - 4, functions.compute(10, 20), 1e-9);
    }

    @Test
    void aWrongFormulaIsReported(){
        assertNotNull(new MarkScale(Kind.FORMULA, 1, 6, .5, List.of(), "p / t *").getError());
        assertNotNull(new MarkScale(Kind.FORMULA, 1, 6, .5, List.of(), "x + 1").getError());
        assertNotNull(new MarkScale(Kind.FORMULA, 1, 6, .5, List.of(), "(p + 1").getError());
        assertEquals(1, new MarkScale(Kind.FORMULA, 1, 6, .5, List.of(), "p / t *").compute(10, 20), "The minimum");
    }

    @Test
    void theRaiseThatChangesTheMark(){
        assertEquals(1, MarkScale.SWISS.getChangingRaise(16, 20, Marks.RAISES).orElseThrow(), "5 → 5.25 → 5.5");
        assertTrue(MarkScale.SWISS.getChangingRaise(20, 20, Marks.RAISES).isEmpty());
    }

    @Test
    void savedAndReadBack(){
        MarkScale scale = new MarkScale(Kind.TABLE, 1, 6, .5, List.of(new Threshold(10, 4), new Threshold(18, 6)), "p / t * 5 + 1");
        assertEquals(scale, MarkScale.fromYAML(scale.toYAML()));
        assertEquals(MarkScale.SWISS, MarkScale.fromYAML(null));
    }
}
