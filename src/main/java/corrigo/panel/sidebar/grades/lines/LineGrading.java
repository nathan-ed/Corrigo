/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grading by lines: the text typed for an exercise, one block of lines per sub-question (a blank line between them),
 * each line a method (+points), a mistake (−points), a comment, "Correct" (all the points), "Faux" (from 0) or a
 * presentation mark (PNF). The grade of a sub-question only depends on its block. Does not depend on JavaFX.
 */
public final class LineGrading {

    private LineGrading(){
    }

    public enum Kind {
        METHOD, // Adds its points
        MISTAKE, // Removes its points
        NEUTRAL, // Points equal to 0: written, counts as graded, does not choose where the points start from
        COMMENT, // No points: written only
        FULL, // "Correct", "Cohérent"…: the sub-question starts from its maximum
        ZERO, // "Faux", "Pas de réponse"…: the sub-question starts from 0
        PNF // A presentation mark for the exercise
    }

    /**
     * A line of the text. index: its line number in the text; shown: what is written on the copy (without the private
     * notes in parentheses nor the points); points: null if none; comment: the comment of a presentation mark.
     */
    public record Line(int index, String raw, String shown, Double points, Kind kind, String comment) {
        // Something to write on the copy
        public boolean isWritten(){
            return kind == Kind.PNF || !shown.isEmpty() || points != null;
        }
    }

    /**
     * A block of lines, for the sub-question at leaf (index in the leaves of the exercise), or -1 if there are more
     * blocks than sub-questions.
     */
    public record Block(int leaf, List<Line> lines) {}

    // Words that give all the points / that start from 0, compared without case, accents and final punctuation
    static final Set<String> FULL_WORDS = Set.of("correct", "correcte", "corrects", "correctes", "juste", "justes",
            "coherent", "coherente", "coherents", "coherentes", "exact", "exacte", "exacts", "exactes");
    static final Set<String> ZERO_WORDS = Set.of("faux", "fausse", "fausses", "pas de reponse", "sans reponse",
            "aucune reponse", "incomplet", "incomplete", "insuffisant", "insuffisante", "reponse erronee", "errone",
            "erronee", "rien", "non fait", "non traite", "absent", "absente");

    // The points at the end of a line: "+1", "1", "- 0.5", "−0,5", "-0.5 pt", "0,5pts"
    private static final Pattern POINTS = Pattern.compile("([+\\-−–]\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:pts?\\.?|points?)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Before a number, these characters make it part of a formula ("x = 3", "2^3", "a/2"), not points
    private static final String FORMULA_CHARS = "=<>^/*×÷≤≥≈≠";
    // A number after these words is a reference, not points: "voir question 2", "page 3"
    static final Set<String> REFERENCE_WORDS = Set.of("question", "questions", "exercice", "exercices", "ex", "exo", "page",
            "pages", "p", "q", "ligne", "etape", "partie", "numero", "no", "n°", "figure", "fig", "annexe", "chapitre", "theoreme",
            "propriete", "definition", "methode", "cas");
    // "(−0,5)" at the end: points written as on the copy, not a private note
    private static final Pattern POINTS_IN_PARENTHESES = Pattern.compile("\\(\\s*([+\\-−–]\\s*\\d+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)\\s*(?:pts?\\.?|points?)?\\s*\\)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PRIVATE = Pattern.compile("\\([^()]*\\)");
    // "b)" or "b." alone, or at the start of the first line of a block: the block is for this sub-question
    private static final Pattern LABEL = Pattern.compile("^\\s*([^().]{1,40}?)\\s*[).]\\s*(.*)$");

    // READING

    // A line of text (pnfLabel: name of the presentation marks, "PNF")
    public static Line parseLine(int index, String raw, String pnfLabel){
        String text = raw.strip();
        // Points written in parentheses at the end, as on the copy: "Erreur (−0,5)"
        Double pointsInParentheses = null;
        Matcher inParentheses = POINTS_IN_PARENTHESES.matcher(text);
        if(inParentheses.find()){
            pointsInParentheses = toNumber(inParentheses.group(1));
            text = text.substring(0, inParentheses.start()).strip();
        }
        String visible = removePrivate(text);

        // Presentation mark: "PNF", "PNF : comment"
        String label = normalize(pnfLabel == null || pnfLabel.isBlank() ? "PNF" : pnfLabel);
        String normalized = normalize(visible);
        if(!label.isEmpty() && (normalized.equals(label) || normalized.startsWith(label + " ") || normalized.startsWith(label + ":"))){
            String comment = visible.strip().substring(Math.min(visible.strip().length(), firstWordLength(visible.strip(), label)))
                    .replaceFirst("^\\s*:?\\s*", "").strip();
            return new Line(index, raw, "", null, Kind.PNF, comment);
        }

        Double points = pointsInParentheses;
        String shown = visible;
        if(points == null){
            Matcher matcher = POINTS.matcher(visible);
            if(matcher.find() && isPoints(visible, matcher)){
                points = toNumber((matcher.group(1) == null ? "" : matcher.group(1)) + matcher.group(2));
                shown = visible.substring(0, matcher.start()).strip();
            }
        }
        shown = shown.replaceAll("[\\s:;,]+$", "").strip(); // "Notation :" → "Notation"
        if(points != null){
            Kind kind = points > 0 ? Kind.METHOD : points < 0 ? Kind.MISTAKE : Kind.NEUTRAL;
            return new Line(index, raw, shown, points, kind, null);
        }
        String key = normalize(shown).replaceAll("[\\p{Punct}\\s]+$", "");
        if(FULL_WORDS.contains(key)) return new Line(index, raw, shown, null, Kind.FULL, null);
        if(ZERO_WORDS.contains(key)) return new Line(index, raw, shown, null, Kind.ZERO, null);
        return new Line(index, raw, shown, null, Kind.COMMENT, null);
    }

    // The number of the match is points, not part of the text or of a formula
    private static boolean isPoints(String text, Matcher matcher){
        int start = matcher.start();
        if(start == 0) return true;
        if(!Character.isWhitespace(text.charAt(start - 1))) return false; // "Q2", "x2"
        String before = text.substring(0, start).strip();
        if(before.isEmpty()) return true;
        char last = before.charAt(before.length() - 1);
        if(FORMULA_CHARS.indexOf(last) >= 0) return false;
        if(matcher.group(1) != null) return true; // Signed: points
        String[] words = normalize(before).split("[\\s']+");
        return !REFERENCE_WORDS.contains(words[words.length - 1].replaceAll("\\p{Punct}+$", ""));
    }

    // Splits the text into blocks (blank lines between them) and gives each one its sub-question (leafNames: the names
    // of the sub-questions, in order). A block that starts with "b)" goes to the sub-question b, the next ones follow it.
    // An exercise without sub-questions has one name (its own): all the blocks are for it.
    public static List<Block> parse(String text, List<String> leafNames, String pnfLabel){
        ArrayList<Block> blocks = new ArrayList<>();
        if(text == null) return blocks;
        String[] rawLines = text.split("\\R", -1);
        ArrayList<Line> current = new ArrayList<>();
        int nextLeaf = 0;
        boolean labelChecked = false;
        for(int i = 0; i <= rawLines.length; i++){
            boolean end = i == rawLines.length;
            String raw = end ? "" : rawLines[i];
            if(raw.isBlank()){
                if(!current.isEmpty()){
                    // Without sub-questions, every block is for the exercise
                    int leaf = leafNames.size() == 1 ? 0 : nextLeaf < leafNames.size() ? nextLeaf : -1;
                    blocks.add(new Block(leaf, List.copyOf(current)));
                    nextLeaf++;
                    current.clear();
                }
                labelChecked = false;
                continue;
            }
            if(!labelChecked){ // First line of a block: "b)" chooses its sub-question
                labelChecked = true;
                Matcher matcher = LABEL.matcher(raw);
                if(matcher.matches()){
                    int leaf = findLeaf(leafNames, matcher.group(1));
                    if(leaf >= 0){
                        nextLeaf = leaf;
                        if(matcher.group(2).isBlank()) continue;
                        current.add(parseLine(i, matcher.group(2), pnfLabel));
                        continue;
                    }
                }
            }
            current.add(parseLine(i, raw, pnfLabel));
        }
        return blocks;
    }
    private static int findLeaf(List<String> leafNames, String label){
        String key = normalize(label);
        for(int i = 0; i < leafNames.size(); i++){
            if(normalize(leafNames.get(i)).equals(key)) return i;
        }
        return -1;
    }

    // GRADE

    /**
     * Grade of a sub-question from its lines, or null if nothing counts (only comments or presentation marks): it is
     * not graded. Start: the maximum if a line gives all the points ("Correct"), 0 if a line starts from 0 ("Faux"),
     * the first of them deciding; without them, the first line with points: a mistake → the maximum, a method → 0.
     * Then the points are added, between 0 and the maximum.
     */
    public static Double grade(List<Line> lines, double total){
        Double keywordStart = null, pointsStart = null;
        boolean counts = false;
        double sum = 0;
        for(Line line : lines){
            switch(line.kind()){
                case FULL -> {
                    counts = true;
                    if(keywordStart == null) keywordStart = total;
                }
                case ZERO -> {
                    counts = true;
                    if(keywordStart == null) keywordStart = 0d;
                }
                case METHOD, MISTAKE, NEUTRAL -> {
                    counts = true;
                    sum += line.points();
                    if(pointsStart == null && line.points() != 0) pointsStart = line.points() < 0 ? total : 0;
                }
                default -> {
                }
            }
        }
        if(!counts) return null;
        double start = keywordStart != null ? keywordStart : pointsStart != null ? pointsStart : 0;
        return Math.max(0, Math.min(total, start + sum));
    }

    // Where the points of the block start from: the maximum, 0, or null if nothing counts
    public static Double start(List<Line> lines, double total){
        for(Line line : lines){
            if(line.kind() == Kind.FULL) return total;
            if(line.kind() == Kind.ZERO) return 0d;
        }
        for(Line line : lines){
            if(line.points() != null && line.points() != 0) return line.points() < 0 ? total : 0d;
        }
        return lines.stream().anyMatch(line -> line.points() != null) ? 0d : null;
    }

    // TEXT ON THE COPY

    public static final String CHECK = "$\\checkmark$";
    // A red cross for the lines that start from 0 ("Faux")
    public static final String CROSS = "$\\boldsymbol{\\times}$";

    // What is written on the copy for a line (not for a presentation mark)
    public static String render(Line line, NumberFormat format, boolean withPoints){
        String points = line.points() == null || line.points() == 0 ? "" : formatPoints(line.points(), format);
        return switch(line.kind()){
            case FULL -> line.shown().isEmpty() ? CHECK : CHECK + " " + line.shown();
            case ZERO -> line.shown().isEmpty() ? CROSS : CROSS + " " + line.shown();
            case METHOD, MISTAKE -> line.shown().isEmpty() ? points : withPoints ? line.shown() + " (" + points + ")" : line.shown();
            default -> line.shown();
        };
    }
    public static String formatPoints(double points, NumberFormat format){
        if(points > 0) return "+" + format.format(points);
        if(points < 0) return "−" + format.format(-points);
        return format.format(0);
    }

    // UTILS

    static Double toNumber(String text){
        String normalized = text.replace("−", "-").replace("–", "-").replace(",", ".").replaceAll("\\s", "");
        try{
            return Double.parseDouble(normalized);
        }catch(NumberFormatException e){
            return null;
        }
    }
    static String removePrivate(String text){
        String previous;
        do{
            previous = text;
            text = PRIVATE.matcher(text).replaceAll("");
        }while(!text.equals(previous));
        return text.replaceAll("\\s{2,}", " ").strip();
    }
    // Lower case, without accents, spaces collapsed
    public static String normalize(String text){
        if(text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
    // Length of the label at the start of the text (the label may have accents or another case)
    private static int firstWordLength(String text, String normalizedLabel){
        for(int length = 1; length <= text.length(); length++){
            if(normalize(text.substring(0, length)).equals(normalizedLabel)) return length;
        }
        return normalizedLabel.length();
    }
}
