/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.lines.LineGrading.Block;
import corrigo.panel.sidebar.grades.lines.LineGrading.Kind;
import corrigo.panel.sidebar.grades.lines.LineGrading.Line;

import java.text.NumberFormat;
import java.util.*;

/**
 * The lines already used for an exercise on the other copies: to add them again with a click or Tab, and to see when
 * the same line is given other points (fairness). Does not depend on JavaFX.
 */
public final class LineBank {

    /**
     * A line used on other copies: its text as it is typed again (text), its words (key), its sub-question (leaf), the
     * points given the most to these words, and the number of copies giving them.
     */
    public record Use(String text, String key, int leaf, Kind kind, Double points, int copies) {}

    // The leaf of the lines offered in every exercise: those typed with "g" after their points, the presentation marks
    public static final int EVERYWHERE = -2;

    private LineBank(){
    }

    /**
     * The uses of the texts of an exercise on other copies (one text per copy), the most used first.
     * leafNames: names of the sub-questions of the exercise.
     */
    public static List<Use> collect(Collection<String> texts, List<String> leafNames, String pnfLabel, NumberFormat format){
        Counts counts = new Counts();
        for(String text : texts){
            Set<String> seen = new HashSet<>();
            for(Block block : LineGrading.parse(text, leafNames, pnfLabel)){
                for(Line line : block.lines()) counts.add(block.leaf(), line, seen);
            }
        }
        return counts.toUses(format);
    }

    /**
     * The lines offered in every exercise of the test: those typed with "g" after their points and the presentation
     * marks, in any exercise of any copy (copies: the blocks of the exercises of each copy), the most used first.
     */
    public static List<Use> collectEverywhere(Collection<List<Block>> copies, NumberFormat format){
        Counts counts = new Counts();
        for(List<Block> blocks : copies){
            Set<String> seen = new HashSet<>();
            for(Block block : blocks){
                for(Line line : block.lines()) if(line.kind() == Kind.PNF || LineGrading.isEverywhere(line)) counts.add(EVERYWHERE, line, seen);
            }
        }
        return counts.toUses(format);
    }

    // How many copies use each line (sub-question and words), with which points
    private static final class Counts {
        private final LinkedHashMap<String, LinkedHashMap<Double, Integer>> byKey = new LinkedHashMap<>(); // → points → copies
        private final HashMap<String, Line> examples = new HashMap<>();
        private final HashMap<String, Integer> leaves = new HashMap<>();

        // seen: the lines already counted for this copy (a line counts once per copy)
        void add(int leaf, Line line, Set<String> seen){
            if(line.raw().isBlank()) return;
            String key = leaf + "|" + keyOf(line);
            if(!seen.add(key + "|" + line.points())) return;
            byKey.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(line.points(), 1, Integer::sum);
            examples.putIfAbsent(key, line);
            leaves.put(key, leaf);
        }
        // The points given the most win
        List<Use> toUses(NumberFormat format){
            ArrayList<Use> uses = new ArrayList<>();
            byKey.forEach((key, byPoints) -> {
                Map.Entry<Double, Integer> most = byPoints.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
                Line example = examples.get(key);
                uses.add(new Use(textOf(example, most.getKey(), format), key.substring(key.indexOf('|') + 1), leaves.get(key),
                        example.kind(), most.getKey(), most.getValue()));
            });
            uses.sort(Comparator.comparingInt(Use::copies).reversed());
            return uses;
        }
    }

    // The words of a line, without case, accents nor points: the same line on two copies
    public static String keyOf(Line line){
        if(line.kind() == Kind.PNF) return "pnf:" + LineGrading.normalize(line.comment());
        return LineGrading.normalize(line.shown()).replaceAll("[\\p{Punct}\\s]+$", "");
    }
    // The line as it is typed again
    public static String textOf(Line line, Double points, NumberFormat format){
        if(line.kind() == Kind.PNF) return line.raw().strip();
        if(points == null) return line.shown();
        String formatted = LineGrading.formatPoints(points, format) + (LineGrading.isEverywhere(line) ? " g" : "");
        return line.shown().isEmpty() ? formatted : line.shown() + " " + formatted;
    }

    /**
     * The lines that complete what is typed (typed: the line being typed; leaf: its sub-question), the best first, at
     * most limit: starting with the typed words, then containing all of them; of the same sub-question first, then of
     * the exercise and of the whole test; the most used first. Not the line typed in full.
     */
    public static List<Use> matches(List<Use> uses, String typed, int leaf, int limit){
        String query = LineGrading.normalize(typed);
        if(query.isEmpty()) return List.of();
        String[] words = query.split(" ");
        ArrayList<Map.Entry<Integer, Use>> ranked = new ArrayList<>();
        for(Use use : uses){
            String key = use.key();
            int rank;
            if(use.kind() == Kind.PNF){ // "pn" → "PNF : unités"
                if(!LineGrading.normalize(use.text()).startsWith(query)) continue;
                rank = 0;
            }else if(key.equals(query)) continue; // Typed in full
            else if(key.startsWith(query)) rank = 0;
            else if(Arrays.stream(words).allMatch(key::contains)) rank = 2;
            else continue;
            if(use.leaf() != leaf) rank++;
            ranked.add(Map.entry(rank, use));
        }
        // Stable: within a rank, the uses keep their order (the most used first); a line once (its best place)
        ranked.sort(Map.Entry.comparingByKey());
        LinkedHashMap<String, Use> byText = new LinkedHashMap<>();
        for(Map.Entry<Integer, Use> entry : ranked) byText.putIfAbsent(entry.getValue().text(), entry.getValue());
        return byText.values().stream().limit(limit).toList();
    }

    // The same words with other points on other copies (fairness), or null
    public static Use findConflict(List<Use> uses, Line line, int leaf){
        if(line.points() == null || line.kind() == Kind.PNF) return null;
        String key = keyOf(line);
        if(key.isEmpty()) return null;
        for(Use use : uses){
            if(use.leaf() == leaf && use.key().equals(key) && use.points() != null && Math.abs(use.points() - line.points()) > 1e-9) return use;
        }
        return null;
    }
}
