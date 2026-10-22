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
    // "g" after the points: a line for the whole test, offered in every exercise ("Notation -0.5 g"); never written
    private static final Pattern EVERYWHERE = Pattern.compile("(?<!\\p{L})[gG]\\.?\\s*$");
    // "b)" or "b." alone, or at the start of the first line of a block: the block is for this sub-question
    private static final Pattern LABEL = Pattern.compile("^\\s*([^().]{1,40}?)\\s*[).]\\s*(.*)$");

    // READING

    // A line of text (pnfLabel: name of the presentation marks, "PNF")
    public static Line parseLine(int index, String raw, String pnfLabel){
        String text = raw.strip();
        // "Notation -0.5 g": the line without its "g", if it has points ("5 g" without points stays a text)
        Matcher everywhere = EVERYWHERE.matcher(text);
        if(everywhere.find() && everywhere.start() > 0){
            Line line = parseLine(index, text.substring(0, everywhere.start()), pnfLabel);
            if(line.points() != null && line.kind() != Kind.PNF) return new Line(index, raw, line.shown(), line.points(), line.kind(), null);
        }
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
            // The points at the end, or before a final dot when there is a text before them ("Erreur -1.", not "2.")
            String withoutDot = visible.endsWith(".") ? visible.substring(0, visible.length() - 1) : null;
            for(String candidate : withoutDot == null ? List.of(visible) : List.of(visible, withoutDot)){
                Matcher matcher = POINTS.matcher(candidate);
                if(matcher.find() && isPoints(candidate, matcher) && (candidate == visible || !candidate.substring(0, matcher.start()).isBlank())){
                    points = toNumber((matcher.group(1) == null ? "" : matcher.group(1)) + matcher.group(2));
                    shown = candidate.substring(0, matcher.start()).strip();
                    break;
                }
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

    // Points typed at the start of a line ("-0.5 Notation"): not counted (the points are at the end), shown as a warning
    public static boolean hasPointsFirst(Line line){
        return line.points() == null && line.kind() == Kind.COMMENT && POINTS_FIRST.matcher(line.raw()).find();
    }
    private static final Pattern POINTS_FIRST = Pattern.compile("^\\s*[+\\-−–]\\s*\\d+(?:[.,]\\d+)?\\s*(?:pts?\\.?|points?)?\\s+\\p{L}");

    // A line typed with "g" after its points: offered in every exercise of the test
    public static boolean isEverywhere(Line line){
        return line.points() != null && line.kind() != Kind.PNF && EVERYWHERE.matcher(line.raw().strip()).find();
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
        // "0,5" or "1 pt": points even after "méthode", "question"… (a reference is a whole number alone)
        if(matcher.group(2).matches(".*[.,].*") || !text.substring(matcher.end(2)).isBlank()) return true;
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
                // The whole name of a sub-question ("1. Limites") is a label too
                int whole = findWholeName(leafNames, raw);
                if(whole >= 0 && leafNames.size() > 1){
                    nextLeaf = whole;
                    continue;
                }
                if(matcher.matches()){
                    int leaf = findLeaf(leafNames, matcher.group(1));
                    if(leaf >= 0 && leafNames.size() > 1){
                        nextLeaf = leaf;
                        String rest = matcher.group(2);
                        if(rest.isBlank() || normalize(rest).equals(nameAfterNumber(leafNames.get(leaf)))) continue;
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
        String key = stripLabel(normalize(label));
        for(int i = 0; i < leafNames.size(); i++){
            if(labelKeys(leafNames.get(i)).contains(key)) return i;
        }
        return -1;
    }
    // The whole name of a sub-question that has its own punctuation ("a)", "1. Limites"), typed alone; a name
    // without it ("Calcul") typed alone is a line, not a label
    private static int findWholeName(List<String> leafNames, String line){
        String key = normalize(line);
        for(int i = 0; i < leafNames.size(); i++){
            String name = normalize(leafNames.get(i));
            boolean punctuated = name.endsWith(")") || name.endsWith(".") || NUMBERED.matcher(name).matches();
            if(punctuated && name.equals(key)) return i;
        }
        return -1;
    }
    // A sub-question is named "a", "a)", "1. Limites" or "Mise en équation": its label is "a)", "a)", "1." or
    // "Mise en équation)", and "1. Limites" written in full is its label too
    private static final Pattern NUMBERED = Pattern.compile("^([\\p{L}\\p{N}]{1,4})\\s*([).])\\s*(.*)$");
    static Set<String> labelKeys(String leafName){
        Set<String> keys = new HashSet<>();
        String name = normalize(leafName);
        keys.add(name);
        keys.add(stripLabel(name));
        Matcher numbered = NUMBERED.matcher(name);
        if(numbered.matches()) keys.add(numbered.group(1));
        return keys;
    }
    private static String stripLabel(String text){
        return text.replaceAll("[\\s).]+$", "");
    }
    // The rest of a full name after its number ("limites" for "1. Limites"), or ""
    private static String nameAfterNumber(String leafName){
        Matcher numbered = NUMBERED.matcher(normalize(leafName));
        return numbered.matches() ? numbered.group(3) : "";
    }
    // What to type at the start of a block to give it to this sub-question
    public static String labelOf(String leafName){
        String name = leafName.strip();
        if(name.endsWith(")") || name.endsWith(".")) return name;
        Matcher numbered = NUMBERED.matcher(name);
        if(numbered.matches()) return numbered.group(1) + numbered.group(2);
        return name + ")";
    }

    // GRADE

    // The lines of the blocks of the sub-question at leaf
    public static List<Line> linesOf(List<Block> blocks, int leaf){
        List<Line> lines = new ArrayList<>();
        for(Block block : blocks) if(block.leaf() == leaf) lines.addAll(block.lines());
        return lines;
    }

    /**
     * Grade of a sub-question from its lines, or null if nothing counts (only comments or presentation marks): it is
     * not graded. Start: the maximum if a line gives all the points ("Correct"), 0 if a line starts from 0 ("Faux"),
     * the first of them deciding; without them, the first line with points: a mistake → the maximum, a method → 0.
     * Then the points are added, between 0 and the maximum.
     */
    public static Double grade(List<Line> lines, double total){
        Double start = start(lines, total);
        if(start == null) return null;
        double sum = lines.stream().filter(line -> line.points() != null).mapToDouble(Line::points).sum();
        return Math.max(0, Math.min(total, start + sum));
    }

    // Where the points of the sub-question start from: the maximum, 0, or null if nothing counts
    public static Double start(List<Line> lines, double total){
        for(Line line : lines){
            if(line.kind() == Kind.FULL) return total;
            if(line.kind() == Kind.ZERO) return 0d;
        }
        for(Line line : lines){
            if(line.points() != null && line.points() != 0) return line.points() < 0 ? total : 0d;
        }
        return lines.stream().anyMatch(line -> line.points() != null) ? 0d : null; // Only points at 0
    }

    // TEXT ON THE COPY

    public static final String CHECK = "$\\checkmark$";
    public static final String CROSS = "$\\boldsymbol{\\times}$";

    // What is written on the copy for a line (not for a presentation mark)
    public static String render(Line line, NumberFormat format, boolean withPoints){
        String points = line.points() == null || line.points() == 0 ? "" : formatPoints(line.points(), format);
        String text = switch(line.kind()){
            case METHOD, MISTAKE -> line.shown().isEmpty() ? points : withPoints ? line.shown() + " (" + points + ")" : line.shown();
            default -> line.shown();
        };
        // A green tick for what gives points ("Correct", "Calculs cohérents +1"), a red cross for what starts from 0
        String mark = switch(line.kind()){
            case FULL, METHOD -> CHECK;
            case ZERO -> CROSS;
            default -> "";
        };
        return mark.isEmpty() || text.isEmpty() ? mark + text : mark + " " + text;
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
