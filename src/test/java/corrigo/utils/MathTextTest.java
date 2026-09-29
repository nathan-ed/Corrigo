/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static corrigo.utils.MathText.Type.MATH;
import static corrigo.utils.MathText.Type.TEXT;
import static org.junit.jupiter.api.Assertions.*;

class MathTextTest {

    private static MathText.Segment text(String content){
        return new MathText.Segment(TEXT, content);
    }
    private static MathText.Segment math(String content){
        return new MathText.Segment(MATH, content);
    }

    @Test
    void plainTextHasNoMath(){
        assertEquals(List.of(text("Très bien, mais justifier !")), MathText.parse("Très bien, mais justifier !"));
        assertFalse(MathText.hasMath("Très bien"));
    }

    @Test
    void singleDollarsAreInlineMath(){
        assertEquals(List.of(text("Très bien, "), math("u_{n+1}=q u_n"), text(" mais pourquoi ?")),
                MathText.parse("Très bien, $u_{n+1}=q u_n$ mais pourquoi ?"));
    }

    @Test
    void doubleDollarsAreStillMath(){
        assertEquals(List.of(text("a "), math("x^2"), text(" b")), MathText.parse("a $$x^2$$ b"));
    }

    @Test
    void unclosedDoubleDollarMakesTheRestMathAsBefore(){
        assertEquals(List.of(math("\\frac{1}{2}")), MathText.parse("$$\\frac{1}{2}"));
        assertTrue(MathText.isWholeMath(MathText.parse("$$\\frac{1}{2}")));
    }

    @Test
    void loneOrEscapedDollarsArePlain(){
        assertEquals(List.of(text("Ça coûte 5$")), MathText.parse("Ça coûte 5$"));
        assertEquals(List.of(text("de 5$ à 6$")), MathText.parse("de 5\\$ à 6\\$"));
        assertFalse(MathText.hasMath("Ça coûte 5$"));
    }

    @Test
    void blankFormulaIsPlain(){
        assertEquals(List.of(text("a $ $ b")), MathText.parse("a $ $ b"));
    }

    @Test
    void escapedDollarInsideFormulaDoesNotCloseIt(){
        assertEquals(List.of(math("5\\$ + x")), MathText.parse("$5\\$ + x$"));
    }

    @Test
    void wholeMathIgnoresSurroundingSpaces(){
        assertTrue(MathText.isWholeMath(MathText.parse(" $x$ ")));
        assertFalse(MathText.isWholeMath(MathText.parse("a $x$")));
        assertFalse(MathText.isWholeMath(MathText.parse("$x$ et $y$")));
    }

    @Test
    void libreOfficeMathTextsAreLegacy(){
        assertTrue(MathText.isLegacy("&&x over 2"));
        assertTrue(MathText.hasMath("&&x over 2"));
        assertFalse(MathText.isLegacy("a & b"));
    }

    @Test
    void newLinesStayInTheText(){
        assertEquals(List.of(text("ligne 1\nligne "), math("2")), MathText.parse("ligne 1\nligne $2$"));
    }
}
