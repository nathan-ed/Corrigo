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
     * A line used on other copies: its text as it is typed again (text), its sub-question (leaf), the number of copies
     * using it, and the other points given to the same words (points → copies), if any.
     */
    public record Use(String text, String key, int leaf, Kind kind, Double points, int copies, Map<Double, Integer> otherPoints) {}

    private LineBank(){
    }

    /**
     * The uses of the texts of an exercise on other copies (one text per copy), the most used first.
     * leafNames: names of the sub-questions of the exercise.
     */
    public static List<Use> collect(Collection<String> texts, List<String> leafNames, String pnfLabel, NumberFormat format){
        // key (sub-question + words) → points → copies
        LinkedHashMap<String, LinkedHashMap<Double, Integer>> counts = new LinkedHashMap<>();
        HashMap<String, Line> examples = new HashMap<>();
        HashMap<String, Integer> leaves = new HashMap<>();
        for(String text : texts){
            Set<String> seen = new HashSet<>(); // A line counted once per copy
            for(Block block : LineGrading.parse(text, leafNames, pnfLabel)){
                for(Line line : block.lines()){
                    if(line.raw().isBlank()) continue;
                    String key = block.leaf() + "|" + keyOf(line);
                    Double points = line.points();
                    if(!seen.add(key + "|" + points)) continue;
                    counts.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(points, 1, Integer::sum);
                    examples.putIfAbsent(key, line);
                    leaves.put(key, block.leaf());
                }
            }
        }
        ArrayList<Use> uses = new ArrayList<>();
        counts.forEach((key, byPoints) -> {
            Map.Entry<Double, Integer> most = byPoints.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            Line example = examples.get(key);
            Double points = most.getKey();
            LinkedHashMap<Double, Integer> others = new LinkedHashMap<>(byPoints);
            others.remove(points);
            int copies = byPoints.values().stream().mapToInt(Integer::intValue).sum();
            uses.add(new Use(textOf(example, points, format), key.substring(key.indexOf('|') + 1), leaves.get(key), example.kind(), points, copies, others));
        });
        uses.sort(Comparator.comparingInt(Use::copies).reversed());
        return uses;
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
        String formatted = LineGrading.formatPoints(points, format);
        return line.shown().isEmpty() ? formatted : line.shown() + " " + formatted;
    }

    /**
     * The best line to complete what is typed (typed: the line being typed; leaf: its sub-question), or null: a line of
     * the same sub-question first, then of the exercise, starting with the typed words, then containing all of them.
     */
    public static Use complete(List<Use> uses, String typed, int leaf){
        String query = LineGrading.normalize(typed);
        if(query.length() < 2) return null;
        String[] words = query.split(" ");
        Use best = null;
        int bestRank = Integer.MAX_VALUE;
        for(Use use : uses){
            if(use.kind() == Kind.PNF) continue;
            String key = use.key();
            if(key.equals(query)) continue; // Already typed in full
            int rank;
            if(key.startsWith(query)) rank = 0;
            else if(Arrays.stream(words).allMatch(key::contains)) rank = 2;
            else continue;
            if(use.leaf() != leaf) rank++;
            if(rank < bestRank){ // The uses are sorted by copies: the first of a rank is the most used
                best = use;
                bestRank = rank;
            }
        }
        return best;
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
