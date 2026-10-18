/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.tags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FreeSpotTest {

    // 20 × 20 cells, lines of writing on rows 5-6 and 9-10 from column 2 to 14
    private static boolean[][] page(){
        boolean[][] occupied = new boolean[20][20];
        for(int row : new int[]{5, 6, 9, 10}){
            for(int x = 2; x <= 14; x++) occupied[row][x] = true;
        }
        return occupied;
    }

    @Test
    void staysWhereItIsWhenFree(){
        assertArrayEquals(new int[]{3, 13}, FreeSpot.find(page(), 4, 2, 3, 13, 10));
    }
    @Test
    void doesNotCoverTheNextLine(){
        // Wanted just under the first line, on the second one: goes between them (rows 7-8)
        assertArrayEquals(new int[]{3, 7}, FreeSpot.find(page(), 4, 2, 3, 8, 10));
    }
    @Test
    void goesBesideWhenNoRoomBetweenLines(){
        // 3 rows high: no room between the lines, the closest free place is right of them
        int[] free = FreeSpot.find(page(), 4, 3, 12, 6, 10);
        assertNotNull(free);
        assertTrue(free[0] >= 15 || free[1] >= 11 || free[1] + 3 <= 5);
    }
    @Test
    void noneWhenEverythingIsWritten(){
        boolean[][] full = new boolean[10][10];
        for(boolean[] row : full) java.util.Arrays.fill(row, true);
        assertNull(FreeSpot.find(full, 2, 2, 4, 4, 10));
    }
    @Test
    void growsAroundWriting(){
        boolean[][] one = new boolean[5][5];
        one[2][2] = true;
        boolean[][] grown = FreeSpot.grow(one, 1);
        assertTrue(grown[1][1] && grown[3][3] && grown[2][3]);
        assertFalse(grown[0][0] || grown[4][4]);
    }
}
