/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.lines.LineGrading.Block;
import corrigo.panel.sidebar.grades.lines.LineGrading.Kind;
import corrigo.panel.sidebar.grades.lines.LineGrading.Line;
import fr.clementgre.pdf4teachers.datasaving.Config;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Converts a copy graded with the methods and mistakes (1.6, 1.7) to the grading by lines, in its edit file:
 * <ul>
 * <li>each scored comment (method, mistake, criterion tick, comment of a method or mistake) and each comment written
 * for a grade becomes a line of its sub-question, in the order of the copy, each block starting with the label of
 * its sub-question; the text written on the copy stays as it is, where it is, linked to its line;</li>
 * <li>no grade changes: a sub-grade follows its lines only if they give its value, else it is a grade typed by hand
 * (shown ✎ in the panel, ↺ to take the grade of the lines);</li>
 * <li>the marks of the old computation are removed from the grades (it would compute them again), the presentation
 * marks and the free texts are not changed.</li>
 * </ul>
 * Each line written is read again with LineGrading: one that does not give the same points is not converted (its text
 * stays on the copy, not linked). Does not depend on JavaFX.
 */
public final class LineConversion {

    private LineConversion(){
    }

    static final String CHECK_OLD = "$\\Large\\checkmark$";
    private static final String[] SCORED_KEYS = {"scoredCommentId", "gradePath", "comment", "points", "noPoints", "localText", "localPoints"};
    private static final String[] COMPUTATION_KEYS = {"valueSource", "valueBeforeComments", "pointsStart", "pointsMode", "pointsAutoMode"};
    private static final double EPS = 1e-9;
    private static final DecimalFormat POINTS = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));

    /** What happened to a copy */
    public static final class Report {
        public int exercises, lines, linked;
        public final List<String> notes = new ArrayList<>();
        // Sub-grades whose value is not the one of their lines (path: value, lines)
        public final List<String> typed = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
    }

    private record Source(Map<String, Object> element, int page, String leafPath, String raw) {}

    /** Converts the edit file data (changed in place). pnfLabel: name of the presentation marks. */
    @SuppressWarnings("unchecked")
    public static Report convert(Map<String, Object> base, String pnfLabel){
        Report report = new Report();
        if(base.get("corrections") instanceof Map<?, ?> existing && !existing.isEmpty()){
            report.notes.add("already converted: not changed");
            return report;
        }
        List<Map<String, Object>> grades = new ArrayList<>();
        if(base.get("grades") instanceof List<?> list) for(Object g : list) if(g instanceof Map<?, ?> m) grades.add((Map<String, Object>) m);
        Map<String, Map<String, Object>> byPath = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> children = new HashMap<>();
        for(Map<String, Object> grade : grades){
            byPath.put(path(grade), grade);
            children.computeIfAbsent(str(grade.get("parentPath")), k -> new ArrayList<>()).add(grade);
        }
        children.values().forEach(list -> list.sort(Comparator.comparingDouble(g -> num(g.get("index"), 0))));

        // The texts of the grading, by sub-grade
        Map<String, List<Source>> sources = new HashMap<>();
        if(base.get("texts") instanceof Map<?, ?> texts){
            for(Map.Entry<?, ?> page : texts.entrySet()){
                int pageIndex = (int) num(String.valueOf(page.getKey()).replaceFirst("page", ""), 0);
                if(!(page.getValue() instanceof List<?> elements)) continue;
                for(Object e : elements){
                    if(!(e instanceof Map<?, ?> m)) continue;
                    Map<String, Object> element = (Map<String, Object>) m;
                    String raw = null, leafPath = null;
                    if(element.get("scoredCommentId") != null){
                        leafPath = str(element.get("gradePath"));
                        raw = rawOfScored(element);
                    }else if(element.get("gradeComment") != null){
                        leafPath = str(element.get("gradeComment"));
                        raw = str(element.get("text")).replace('\n', ' ').strip();
                    }
                    if(raw == null || leafPath.isEmpty()) continue;
                    sources.computeIfAbsent(leafPath, k -> new ArrayList<>()).add(new Source(element, pageIndex, leafPath, raw));
                }
            }
        }

        LinkedHashMap<String, Object> corrections = new LinkedHashMap<>();
        for(Map<String, Object> exercise : children.getOrDefault("\\" + rootName(grades), List.of())){
            String exercisePath = path(exercise);
            List<Map<String, Object>> leaves = new ArrayList<>();
            collectLeaves(exercise, children, leaves);
            List<String> names = leaves.stream().map(g -> str(g.get("name"))).toList();
            // The lines of the exercise and their ids, side by side
            List<String> lineTexts = new ArrayList<>(), ids = new ArrayList<>();
            Map<Map<String, Object>, String> links = new IdentityHashMap<>();
            int count = 0;
            for(int i = 0; i < leaves.size(); i++){
                String leafPath = path(leaves.get(i));
                List<Source> leafSources = new ArrayList<>(sources.getOrDefault(leafPath, List.of()));
                // A comment written for the exercise (with sub-grades): at the end of the last sub-question
                if(i == leaves.size() - 1 && leaves.size() > 1) leafSources.addAll(sources.getOrDefault(exercisePath, List.of()));
                leafSources.sort(Comparator.comparingInt(Source::page).thenComparingDouble(s -> num(s.element().get("y"), 0))
                        .thenComparingDouble(s -> num(s.element().get("x"), 0)));
                List<Source> kept = new ArrayList<>();
                List<String> raws = new ArrayList<>();
                for(Source source : leafSources){
                    String raw = checked(source, report, pnfLabel);
                    if(raw == null) continue;
                    kept.add(source);
                    raws.add(raw);
                }
                if(kept.isEmpty()) continue;
                if(!lineTexts.isEmpty()){ // A blank line between two sub-questions
                    lineTexts.add("");
                    ids.add("");
                }
                if(leaves.size() > 1){ // Its label: the block is for this sub-question whatever is before it
                    lineTexts.add(LineGrading.labelOf(names.get(i)));
                    ids.add(newId());
                }
                for(int k = 0; k < kept.size(); k++){
                    String id = newId();
                    lineTexts.add(raws.get(k));
                    ids.add(id);
                    links.put(kept.get(k).element(), id);
                    count++;
                }
            }
            if(count == 0) continue;
            String text = String.join("\n", lineTexts);
            // The grade of each sub-question from its lines
            LinkedHashMap<String, Double> computed = new LinkedHashMap<>();
            List<Block> blocks = LineGrading.parse(text, names, pnfLabel);
            for(int i = 0; i < leaves.size(); i++){
                List<Line> leafLines = LineGrading.linesOf(blocks, i);
                if(leafLines.isEmpty()) continue;
                Map<String, Object> leaf = leaves.get(i);
                Double grade = LineGrading.grade(leafLines, num(leaf.get("total"), 0));
                double value = num(leaf.get("value"), -1);
                if(grade != null && value >= 0 && Math.abs(grade - value) < EPS) computed.put(path(leaf), grade);
                else if(grade != null || value >= 0) report.typed.add(path(leaf) + " : " + fmt(value) + " (lignes : " + (grade == null ? "pas notée" : fmt(grade)) + ")");
            }
            // The texts on the copy are linked to their lines (only the lines read back as lines of their sub-question)
            Map<String, Integer> leafOfId = new HashMap<>();
            for(Block block : blocks) for(Line line : block.lines()) if(line.isWritten()) leafOfId.put(ids.get(line.index()), block.leaf());
            for(Map.Entry<Map<String, Object>, String> link : links.entrySet()){
                Map<String, Object> element = link.getKey();
                Integer leaf = leafOfId.get(link.getValue());
                if(leaf == null || leaf < 0 || !path(leaves.get(leaf)).equals(sourceLeaf(element, exercisePath, leaves))){
                    report.skipped.add(exercisePath + " : texte non relié (ligne relue ailleurs)");
                    continue;
                }
                for(String key : SCORED_KEYS) element.remove(key);
                element.remove("gradeComment");
                element.put("correctionLine", link.getValue());
                report.linked++;
            }
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("text", text);
            entry.put("ids", new ArrayList<>(ids));
            entry.put("computed", computed);
            corrections.put(exercisePath, entry);
            report.exercises++;
            report.lines += count;
        }
        // The old computation of the grades is not used any more (the values stay)
        for(Map<String, Object> grade : grades) for(String key : COMPUTATION_KEYS) grade.remove(key);
        if(!corrections.isEmpty()) base.put("corrections", corrections);
        return report;
    }

    // The line written for a scored comment (tick, method, mistake, comment)
    private static String rawOfScored(Map<String, Object> element){
        String comment = str(element.get("comment")).replace('\n', ' ').strip();
        boolean noPoints = Boolean.TRUE.equals(element.get("noPoints"));
        if(CHECK_OLD.equals(comment)) return "Correct"; // A ticked criterion: all the points of its sub-question
        if(noPoints) return comment.isEmpty() ? null : comment;
        double points = num(element.get("points"), 0);
        String p = points > 0 ? "+" + POINTS.format(points) : POINTS.format(points);
        return comment.isEmpty() ? p : comment + " " + p;
    }
    // The line, read again: the same points (or none for a comment), else null (not converted)
    private static String checked(Source source, Report report, String pnfLabel){
        Map<String, Object> element = source.element();
        boolean scored = element.get("scoredCommentId") != null;
        boolean tick = scored && CHECK_OLD.equals(str(element.get("comment")));
        boolean counted = scored && !tick && !Boolean.TRUE.equals(element.get("noPoints"));
        Double expected = counted ? num(element.get("points"), 0) : null;
        for(String raw : candidates(source.raw(), expected)){
            Line line = LineGrading.parseLine(0, raw, pnfLabel);
            if(line.kind() == Kind.PNF) continue;
            if(tick ? line.kind() == Kind.FULL : expected == null ? line.points() == null
                    : line.points() != null && Math.abs(line.points() - expected) < EPS) return raw;
        }
        report.skipped.add(source.leafPath() + " : « " + source.raw() + " »");
        return null;
    }
    // The line, then the same with a separator before the points (a comment ending with "=" or "page 2")
    private static List<String> candidates(String raw, Double points){
        if(points == null) return List.of(raw);
        int space = raw.lastIndexOf(' ');
        if(space < 0) return List.of(raw);
        return List.of(raw, raw.substring(0, space) + " ; " + raw.substring(space + 1));
    }

    // The sub-grade a text of the copy counts on (a comment of the exercise: its last sub-grade)
    private static String sourceLeaf(Map<String, Object> element, String exercisePath, List<Map<String, Object>> leaves){
        String target = element.get("scoredCommentId") != null ? str(element.get("gradePath")) : str(element.get("gradeComment"));
        return target.equals(exercisePath) && leaves.size() > 1 ? path(leaves.getLast()) : target;
    }

    private static void collectLeaves(Map<String, Object> grade, Map<String, List<Map<String, Object>>> children, List<Map<String, Object>> leaves){
        List<Map<String, Object>> kids = children.get(path(grade));
        if(kids == null || kids.isEmpty()){
            leaves.add(grade);
            return;
        }
        for(Map<String, Object> kid : kids) collectLeaves(kid, children, leaves);
    }
    private static String rootName(List<Map<String, Object>> grades){
        for(Map<String, Object> grade : grades) if(str(grade.get("parentPath")).isEmpty()) return str(grade.get("name"));
        return "Total";
    }
    private static String path(Map<String, Object> grade){
        return str(grade.get("parentPath")) + "\\" + str(grade.get("name"));
    }
    private static String str(Object value){
        return value == null ? "" : String.valueOf(value);
    }
    private static double num(Object value, double defaultValue){
        if(value instanceof Number number) return number.doubleValue();
        try{
            return Double.parseDouble(String.valueOf(value));
        }catch(NumberFormatException e){
            return defaultValue;
        }
    }
    private static String fmt(double value){
        return value < 0 ? "vide" : POINTS.format(value).replace('.', ',');
    }
    private static String newId(){
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // FILES

    /**
     * Converts the edit files of a folder into another folder (the folder given is only read): the edit files of the
     * copies, and .pdf4teachers/tags.yml without the occurrences of the copies converted (they would be written again
     * on the copies). Prints a report.
     *     java … corrigo.panel.sidebar.grades.lines.LineConversion <folder> <output folder> [PNF name]
     */
    public static void main(String[] args) throws Exception{
        File in = new File(args[0]), out = new File(args[1]);
        String pnfLabel = args.length > 2 ? args[2] : "PNF";
        if(out.exists() && Objects.requireNonNull(out.list()).length > 0) throw new IllegalArgumentException("The output folder must be empty");
        out.mkdirs();
        File[] files = in.listFiles((dir, name) -> name.startsWith(".") && name.endsWith(".pdf.yml"));
        Arrays.sort(Objects.requireNonNull(files));
        Set<String> converted = new HashSet<>();
        int n = 0;
        for(File file : files){
            n++;
            Config config = new Config(file);
            config.load();
            Report report = convert(config.base, pnfLabel);
            Config output = new Config(new File(out, file.getName()));
            output.base = config.base;
            output.save();
            if(report.exercises > 0) converted.add(file.getName().substring(1, file.getName().length() - ".yml".length()));
            System.out.printf("Copie %02d : %d exercice(s), %d ligne(s), %d texte(s) reliés%s%n", n, report.exercises, report.lines,
                    report.linked, report.notes.isEmpty() ? "" : " — " + String.join(", ", report.notes));
            for(String typed : report.typed) System.out.println("    ✎ note gardée, différente des lignes : " + typed);
            for(String skipped : report.skipped) System.out.println("    ligne non convertie (texte gardé sur la copie) : " + skipped);
        }
        File tags = new File(in, ".pdf4teachers" + File.separator + "tags.yml");
        if(tags.exists()){
            Config config = new Config(tags);
            config.load();
            if(config.base.get("copies") instanceof Map<?, ?> copies) copies.keySet().removeIf(converted::contains);
            File dir = new File(out, ".pdf4teachers");
            dir.mkdirs();
            Config output = new Config(new File(dir, "tags.yml"));
            output.base = config.base;
            output.save();
        }
        System.out.println(converted.size() + " copie(s) converties sur " + files.length + ".");
    }
}
