/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.export;

import corrigo.panel.sidebar.grades.lines.LineGrading;
import corrigo.panel.sidebar.grades.lines.LineGrading.Block;
import corrigo.panel.sidebar.grades.lines.LineGrading.Line;

import java.util.*;

import static corrigo.panel.sidebar.grades.export.FairnessExport.*;

/**
 * What is left to check on a copy graded by lines, read from its edit file: the copy not graded at all, the lines not
 * written on the copy yet, the grades typed by hand over the lines (✎, as the panel shows them), the sub-questions not
 * graded. In the order of the exercises. Does not depend on JavaFX.
 */
public final class CopyChecks {

    private CopyChecks(){
    }

    public enum Kind { NOT_STARTED, UNPLACED, TYPED, NOT_GRADED }

    /**
     * exercise: index of the exercise in the grade scale (-1 for NOT_STARTED); leaf: name of the sub-question (empty for
     * the exercise itself); value: the grade (TYPED); linesGrade: the grade of the lines (TYPED, null: not graded);
     * count: the lines not placed (UNPLACED).
     */
    public record Check(Kind kind, int exercise, String exerciseName, String leaf, double value, Double linesGrade, int count) {}

    private static final double EPS = 1e-9;

    public static List<Check> of(Map<String, Object> base, String pnfLabel){
        ArrayList<Check> checks = new ArrayList<>();
        Grades grades = new Grades(base);
        Map<?, ?> corrections = base.get("corrections") instanceof Map<?, ?> map ? map : Map.of();
        if(grades.root == null || !isStarted(grades, corrections)){
            checks.add(new Check(Kind.NOT_STARTED, -1, "", "", -1, null, 0));
            return checks;
        }
        Set<String> placed = new HashSet<>();
        for(Map<String, Object> t : texts(base)) if(t.get("correctionLine") != null) placed.add(str(t.get("correctionLine")));

        List<Map<String, Object>> exercises = grades.children(grades.root);
        for(int i = 0; i < exercises.size(); i++){
            Map<String, Object> exercise = exercises.get(i);
            List<Map<String, Object>> leaves = grades.leaves(exercise);
            boolean alone = leaves.size() == 1 && leaves.getFirst() == exercise;
            Map<?, ?> entry = corrections.get(path(exercise)) instanceof Map<?, ?> m ? m : Map.of();
            String text = entry.get("text") instanceof String t ? t : "";
            Map<?, ?> computed = entry.get("computed") instanceof Map<?, ?> c ? c : Map.of();
            List<?> ids = entry.get("ids") instanceof List<?> l ? l : List.of();
            List<Block> blocks = LineGrading.parse(text, leaves.stream().map(FairnessExport::name).toList(), pnfLabel);

            // Lines not written on the copy (ids not matching the lines: the app makes them again, nothing is linked)
            boolean linked = ids.size() == text.split("\\R", -1).length;
            int unplaced = 0;
            for(Block block : blocks){
                for(Line line : block.lines()){
                    if(line.isWritten() && (!linked || !placed.contains(String.valueOf(ids.get(line.index()))))) unplaced++;
                }
            }
            if(unplaced > 0) checks.add(new Check(Kind.UNPLACED, i, name(exercise), "", -1, null, unplaced));

            for(int k = 0; k < leaves.size(); k++){
                Map<String, Object> leaf = leaves.get(k);
                String leafName = alone ? "" : name(leaf);
                double value = value(leaf);
                if(value < 0){
                    checks.add(new Check(Kind.NOT_GRADED, i, name(exercise), leafName, -1, null, 0));
                    continue;
                }
                // Typed by hand: not the grade the lines gave last (a grade there before the lines counts as typed)
                Object given = computed.get(path(leaf));
                boolean typed = !text.isBlank() && (given instanceof Number n ? Math.abs(value - n.doubleValue()) > EPS : true);
                if(!typed) continue;
                List<Line> lines = LineGrading.linesOf(blocks, k);
                Double fromLines = lines.isEmpty() ? null : LineGrading.grade(lines, total(leaf));
                checks.add(new Check(Kind.TYPED, i, name(exercise), leafName, value, fromLines, 0));
            }
        }
        return checks;
    }

    // Something was graded or written by lines on the copy
    private static boolean isStarted(Grades grades, Map<?, ?> corrections){
        for(Map<String, Object> leaf : grades.leaves(grades.root)) if(value(leaf) >= 0) return true;
        for(Object entry : corrections.values()){
            if(entry instanceof Map<?, ?> m && m.get("text") instanceof String text && !text.isBlank()) return true;
        }
        return false;
    }
}
