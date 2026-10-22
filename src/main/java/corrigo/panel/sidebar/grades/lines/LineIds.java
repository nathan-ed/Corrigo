/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Each line of the text of an exercise has an id, kept while the text is edited, which links it to what it wrote on
 * the copy: a line typed again elsewhere, moved by the insertion or the deletion of other lines, or corrected (a typo)
 * keeps its id; a new line gets a new one; a deleted line loses its id (and what it wrote is removed).
 * Blank lines have the id "". Does not depend on JavaFX.
 */
public final class LineIds {

    private LineIds(){
    }

    public static List<String> update(List<String> oldLines, List<String> oldIds, List<String> newLines){
        return update(oldLines, oldIds, newLines, 0, 0);
    }
    /**
     * keepFirst, keepLast: number of lines at the start and at the end of the text known to be the same lines (before
     * and after the part edited): they keep their ids whatever the lines around them. Two equal lines, one deleted:
     * the one deleted loses its id, not the other one.
     */
    public static List<String> update(List<String> oldLines, List<String> oldIds, List<String> newLines, int keepFirst, int keepLast){
        return update(oldLines, oldIds, newLines, keepFirst, keepLast, () -> UUID.randomUUID().toString().substring(0, 8));
    }

    static List<String> update(List<String> oldLines, List<String> oldIds, List<String> newLines, Supplier<String> newId){
        return update(oldLines, oldIds, newLines, 0, 0, newId);
    }
    static List<String> update(List<String> oldLines, List<String> oldIds, List<String> newLines, int keepFirst, int keepLast,
                               Supplier<String> newId){
        int n = oldLines.size(), m = newLines.size();
        // Ids of the old text (missing or wrong ones are recreated)
        ArrayList<String> ids = new ArrayList<>();
        for(int i = 0; i < n; i++){
            String id = i < oldIds.size() ? oldIds.get(i) : null;
            ids.add(oldLines.get(i).isBlank() ? "" : id == null || id.isEmpty() ? newId.get() : id);
        }
        // The lines kept before and after the part edited (only if they are the same), the part edited compared alone
        int first = Math.max(0, keepFirst), last = Math.max(0, keepLast);
        if(first + last > Math.min(n, m) || !same(oldLines, newLines, 0, 0, first) || !same(oldLines, newLines, n - last, m - last, last)){
            first = 0;
            last = 0;
        }
        if(first > 0 || last > 0){
            ArrayList<String> out = new ArrayList<>(ids.subList(0, first));
            out.addAll(diff(oldLines.subList(first, n - last), ids.subList(first, n - last), newLines.subList(first, m - last), newId));
            out.addAll(ids.subList(n - last, n));
            return out;
        }
        return diff(oldLines, ids, newLines, newId);
    }
    private static boolean same(List<String> oldLines, List<String> newLines, int oldStart, int newStart, int count){
        for(int k = 0; k < count; k++) if(!same(oldLines.get(oldStart + k), newLines.get(newStart + k))) return false;
        return true;
    }

    // ids: one per old line, blank lines ""
    private static List<String> diff(List<String> oldLines, List<String> ids, List<String> newLines, Supplier<String> newId){
        int n = oldLines.size(), m = newLines.size();
        // Longest common subsequence of the lines (equal text, spaces ignored at the ends)
        int[][] lcs = new int[n + 1][m + 1];
        for(int i = n - 1; i >= 0; i--){
            for(int j = m - 1; j >= 0; j--){
                lcs[i][j] = same(oldLines.get(i), newLines.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        String[] result = new String[m];
        int i = 0, j = 0;
        // Lines between two common lines: replaced one by one (a corrected line keeps its id), the others added or removed
        ArrayList<Integer> removed = new ArrayList<>(), added = new ArrayList<>();
        while(i < n || j < m){
            if(i < n && j < m && same(oldLines.get(i), newLines.get(j))){
                pair(removed, added, ids, oldLines, newLines, result);
                result[j] = ids.get(i);
                i++;
                j++;
            }else if(j < m && (i == n || lcs[i][j + 1] >= lcs[i + 1][j])){
                added.add(j++);
            }else{
                removed.add(i++);
            }
        }
        pair(removed, added, ids, oldLines, newLines, result);
        ArrayList<String> out = new ArrayList<>();
        for(int k = 0; k < m; k++){
            if(newLines.get(k).isBlank()) out.add("");
            else out.add(result[k] == null || result[k].isEmpty() ? newId.get() : result[k]);
        }
        return out;
    }

    // Pairs the removed and added non-blank lines of a changed part, in order
    private static void pair(List<Integer> removed, List<Integer> added, List<String> ids, List<String> oldLines,
                             List<String> newLines, String[] result){
        List<Integer> oldIndexes = removed.stream().filter(index -> !oldLines.get(index).isBlank()).toList();
        List<Integer> newIndexes = added.stream().filter(index -> !newLines.get(index).isBlank()).toList();
        for(int k = 0; k < Math.min(oldIndexes.size(), newIndexes.size()); k++){
            result[newIndexes.get(k)] = ids.get(oldIndexes.get(k));
        }
        removed.clear();
        added.clear();
    }

    private static boolean same(String a, String b){
        return a.strip().equals(b.strip());
    }
}
