/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.tags;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Where to write a comment near a spot of the page without covering what is written there: the free place closest to
 * the preferred one, on a grid of cells (true: ink of the student or another annotation). Does not depend on JavaFX.
 */
public final class FreeSpot {

    private FreeSpot(){
    }

    /**
     * Top left cell {x, y} of the free box (boxW × boxH cells) the closest to (prefX, prefY), at most maxDistance cells
     * away, or null if there is none. The box stays in the page.
     */
    public static int[] find(boolean[][] occupied, int boxW, int boxH, int prefX, int prefY, int maxDistance){
        int rows = occupied.length, cols = rows == 0 ? 0 : occupied[0].length;
        if(boxW <= 0 || boxH <= 0 || boxW > cols || boxH > rows) return null;
        // Number of occupied cells above and left of each cell, to check a box at once
        int[][] sum = new int[rows + 1][cols + 1];
        for(int y = 0; y < rows; y++){
            for(int x = 0; x < cols; x++){
                sum[y + 1][x + 1] = sum[y][x + 1] + sum[y + 1][x] - sum[y][x] + (occupied[y][x] ? 1 : 0);
            }
        }
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for(int y = Math.max(0, prefY - maxDistance); y <= Math.min(rows - boxH, prefY + maxDistance); y++){
            for(int x = Math.max(0, prefX - maxDistance); x <= Math.min(cols - boxW, prefX + maxDistance); x++){
                double distance = Math.hypot(x - prefX, y - prefY);
                if(distance >= bestDistance || distance > maxDistance) continue;
                int count = sum[y + boxH][x + boxW] - sum[y][x + boxW] - sum[y + boxH][x] + sum[y][x];
                if(count == 0){
                    best = new int[]{x, y};
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    // The cells around an occupied cell are occupied too: a comment does not touch what is written
    public static boolean[][] grow(boolean[][] occupied, int margin){
        int rows = occupied.length, cols = rows == 0 ? 0 : occupied[0].length;
        boolean[][] grown = new boolean[rows][cols];
        for(int y = 0; y < rows; y++){
            for(int x = 0; x < cols; x++){
                if(!occupied[y][x]) continue;
                for(int dy = -margin; dy <= margin; dy++){
                    for(int dx = -margin; dx <= margin; dx++){
                        int ny = y + dy, nx = x + dx;
                        if(ny >= 0 && ny < rows && nx >= 0 && nx < cols) grown[ny][nx] = true;
                    }
                }
            }
        }
        return grown;
    }

    // The groups of occupied cells (touching by a side) smaller than minCells are free: specks of dust of the scan
    public static boolean[][] removeSpecks(boolean[][] occupied, int minCells){
        int rows = occupied.length, cols = rows == 0 ? 0 : occupied[0].length;
        boolean[][] kept = new boolean[rows][cols];
        boolean[][] seen = new boolean[rows][cols];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        ArrayList<int[]> group = new ArrayList<>();
        for(int y = 0; y < rows; y++){
            for(int x = 0; x < cols; x++){
                if(!occupied[y][x] || seen[y][x]) continue;
                group.clear();
                queue.add(new int[]{x, y});
                seen[y][x] = true;
                while(!queue.isEmpty()){
                    int[] cell = queue.poll();
                    group.add(cell);
                    int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for(int[] d : around){
                        int nx = cell[0] + d[0], ny = cell[1] + d[1];
                        if(nx >= 0 && ny >= 0 && nx < cols && ny < rows && occupied[ny][nx] && !seen[ny][nx]){
                            seen[ny][nx] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }
                if(group.size() >= minCells) for(int[] cell : group) kept[cell[1]][cell[0]] = true;
            }
        }
        return kept;
    }
}
