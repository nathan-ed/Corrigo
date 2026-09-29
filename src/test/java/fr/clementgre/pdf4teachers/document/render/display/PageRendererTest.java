/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.document.render.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageRendererTest {
    
    @Test
    void renderWidthUsesSameFormulaForPreloadAndRenderCacheKeys(){
        assertEquals((int) (PageRenderer.PAGE_WIDTH * 1.4 * 1.5), PageRenderer.getRenderWidth(1.5));
    }
    
    @Test
    void renderWidthNeverDropsBelowOnePixel(){
        assertEquals(1, PageRenderer.getRenderWidth(0));
        assertEquals(1, PageRenderer.getRenderWidth(-10));
    }
}
