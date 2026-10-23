/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.export;

import corrigo.panel.sidebar.grades.lines.LineBank;
import corrigo.panel.sidebar.grades.lines.LineGrading;
import corrigo.panel.sidebar.grades.lines.LineGrading.Block;
import corrigo.panel.sidebar.grades.lines.LineGrading.Kind;
import corrigo.panel.sidebar.grades.lines.LineGrading.Line;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;

/**
 * The grading of all the copies of an evaluation, anonymous, in one Markdown text: to give to an AI (or a colleague)
 * that reads the fairness of the grading. Each copy is "Copie 01", "Copie 02"… (in the order given): the names of the
 * copies (the names of the files) are not in it, the texts are as the teacher wrote them. For each copy: each exercise and sub-question, its grade, where
 * its points start from, its lines (or the old scored comments), a grade typed by hand (✎), the presentation marks and
 * the other texts written on the copy. Then the lines used in the class with the copies that got them, the same words
 * with other points first. Does not depend on JavaFX.
 */
public final class FairnessExport {

    private FairnessExport(){
    }

    private static final String CORRECTIONS = "corrections";
    private static final double EPS = 1e-9;

    /**
     * tr: the text of a key of the export ("fairness.*", "{0}", "{1}" replaced), in the language of the app.
     * pnfLabel: the name of the presentation marks.
     */
    // copies: the data of the edit file of each copy, in the order of their numbers
    public static String build(List<Map<String, Object>> copies, String pnfLabel, NumberFormat format, Function<String, String> tr){
        Writer w = new Writer(format, tr);
        w.line("# " + tr.apply("fairness.title"));
        w.line("");
        w.line(tr.apply("fairness.intro"));
        w.line("");
        w.line("## " + tr.apply("fairness.rule.title"));
        w.line("");
        w.line(tr.apply("fairness.rule"));
        w.line("");
        if(!copies.isEmpty()) scale(w, copies.getFirst());

        // Lines used in the class: exercise → sub-question → words → points → copies
        LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Use>>> used = new LinkedHashMap<>();
        for(int i = 0; i < copies.size(); i++) copy(w, number(i), copies.get(i), pnfLabel, used);
        uses(w, used);
        return w.text();
    }

    /** The name of the copy at this index: "Copie 01"… */
    public static String number(int index){
        return String.format(Locale.ROOT, "Copie %02d", index + 1);
    }

    // SCALE

    private static void scale(Writer w, Map<String, Object> base){
        Grades grades = new Grades(base);
        if(grades.root == null) return;
        w.line("## " + w.tr("fairness.scale"));
        w.line("");
        for(Map<String, Object> exercise : grades.children(grades.root)){
            List<Map<String, Object>> leaves = grades.leaves(exercise);
            StringBuilder line = new StringBuilder("- " + name(exercise) + " : /" + w.num(total(exercise)));
            if(leaves.size() > 1 || leaves.getFirst() != exercise){
                line.append(" (");
                for(int k = 0; k < leaves.size(); k++) line.append(k == 0 ? "" : ", ").append(name(leaves.get(k))).append(" /").append(w.num(total(leaves.get(k))));
                line.append(")");
            }
            w.line(line.toString());
        }
        w.line("- " + w.tr("fairness.total") + " : /" + w.num(total(grades.root)));
        w.line("");
    }

    // A COPY

    private static void copy(Writer w, String number, Map<String, Object> base, String pnfLabel,
                             LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Use>>> used){
        Grades grades = new Grades(base);
        List<Map<String, Object>> texts = texts(base);
        w.line("## " + number + (grades.root == null ? "" : " — " + w.grade(grades.root)));
        w.line("");
        if(grades.root == null){
            w.line(w.tr("fairness.noScale"));
            w.line("");
            return;
        }
        Map<?, ?> corrections = base.get(CORRECTIONS) instanceof Map<?, ?> map ? map : Map.of();
        Set<Map<String, Object>> told = Collections.newSetFromMap(new IdentityHashMap<>());
        for(Map<String, Object> exercise : grades.children(grades.root)){
            String path = path(exercise);
            List<Map<String, Object>> leaves = grades.leaves(exercise);
            boolean alone = leaves.size() == 1 && leaves.getFirst() == exercise;
            w.line("### " + name(exercise) + " — " + w.grade(exercise));
            Map<?, ?> entry = corrections.get(path) instanceof Map<?, ?> m ? m : null;
            String text = entry != null && entry.get("text") instanceof String t ? t : "";
            Map<?, ?> computed = entry != null && entry.get("computed") instanceof Map<?, ?> c ? c : Map.of();
            List<Block> blocks = LineGrading.parse(text, leaves.stream().map(FairnessExport::name).toList(), pnfLabel);
            for(int k = 0; k < leaves.size(); k++){
                Map<String, Object> leaf = leaves.get(k);
                List<Line> lines = LineGrading.linesOf(blocks, k);
                StringBuilder head = new StringBuilder("- ");
                if(!alone) head.append(name(leaf)).append(" : ");
                head.append(w.grade(leaf));
                double value = value(leaf);
                if(!lines.isEmpty()){
                    Double start = LineGrading.start(lines, total(leaf));
                    if(start != null) head.append(" — ").append(w.tr(start >= total(leaf) ? "fairness.fromMax" : "fairness.fromZero"));
                    Double fromLines = LineGrading.grade(lines, total(leaf));
                    Object given = computed.get(path(leaf));
                    boolean typed = given instanceof Number n ? Math.abs(value - n.doubleValue()) > EPS : value >= 0;
                    if(typed) head.append(" — ").append(w.tr("fairness.typed", fromLines == null ? w.tr("fairness.notGraded") : w.num(fromLines)));
                }
                if(!alone || !lines.isEmpty()) w.line(head.toString()); // Alone without lines: its grade is in the title
                for(Line line : lines){
                    if(line.raw().isBlank()) continue;
                    w.line("  - " + line.raw().strip());
                    if(line.kind() != Kind.PNF) use(used, name(exercise), alone ? "" : name(leaf), line, number, w);
                }
                // Copies graded with the methods and mistakes (before the lines): their scored comments, their comments
                for(Map<String, Object> t : texts){
                    String target = t.get("scoredCommentId") != null ? str(t.get("gradePath")) : str(t.get("gradeComment"));
                    if(!target.equals(path(leaf)) || t.get("correctionLine") != null) continue;
                    told.add(t);
                    w.line("  - " + scoredText(t, w) + " " + w.tr("fairness.old"));
                }
            }
            for(Block block : blocks){
                if(block.leaf() >= 0) continue;
                for(Line line : block.lines()) w.line("- " + w.tr("fairness.extra") + " : " + line.raw().strip());
            }
            w.line("");
        }
        // Presentation marks and the other texts of the copy
        List<String> marks = new ArrayList<>(), others = new ArrayList<>();
        for(Map<String, Object> t : texts){
            if(told.contains(t) || t.get("correctionLine") != null && !isMark(t, pnfLabel)) continue;
            String value = str(t.get("text")).strip();
            if(value.isEmpty() || isTable(t, pnfLabel)) continue;
            if(isMark(t, pnfLabel)){
                String where = t.get("pnfExercise") instanceof String e ? " (" + e + ")" : "";
                marks.add(value + where);
            }else if(t.get("scoredCommentId") == null && t.get("gradeComment") == null) others.add(value.replace('\n', ' '));
        }
        w.line("- " + w.tr("fairness.marks", String.valueOf(marks.size())) + (marks.isEmpty() ? "" : " : " + String.join(" ; ", marks)));
        if(!others.isEmpty()){
            w.line("- " + w.tr("fairness.others") + " :");
            for(String other : others) w.line("  - " + other);
        }
        w.line("");
    }

    // LINES USED IN THE CLASS

    private static final class Use {
        final String text;
        final LinkedHashMap<String, List<String>> copiesByPoints = new LinkedHashMap<>();
        Use(String text){
            this.text = text;
        }
    }
    private static void use(LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Use>>> used, String exercise, String leaf,
                            Line line, String number, Writer w){
        String key = LineBank.keyOf(line);
        if(key.isEmpty() && line.points() == null) return;
        Use use = used.computeIfAbsent(exercise, k -> new LinkedHashMap<>()).computeIfAbsent(leaf, k -> new LinkedHashMap<>())
                .computeIfAbsent(key, k -> new Use(line.shown().isEmpty() ? line.raw().strip() : line.shown()));
        String points = switch(line.kind()){
            case FULL -> w.tr("fairness.allPoints");
            case ZERO -> w.tr("fairness.fromZero");
            case COMMENT -> w.tr("fairness.comment");
            default -> LineGrading.formatPoints(line.points(), w.format);
        };
        List<String> list = use.copiesByPoints.computeIfAbsent(points, k -> new ArrayList<>());
        if(!list.contains(number)) list.add(number);
    }
    private static void uses(Writer w, LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Use>>> used){
        if(used.isEmpty()) return;
        w.line("## " + w.tr("fairness.uses.title"));
        w.line("");
        w.line(w.tr("fairness.uses.intro"));
        w.line("");
        used.forEach((exercise, leaves) -> leaves.forEach((leaf, uses) -> {
            w.line("### " + exercise + (leaf.isEmpty() ? "" : " " + leaf));
            // The same words with other points first
            List<Use> sorted = new ArrayList<>(uses.values());
            sorted.sort(Comparator.comparingInt((Use use) -> use.copiesByPoints.size() > 1 ? 0 : 1));
            for(Use use : sorted){
                StringBuilder line = new StringBuilder("- ").append(use.copiesByPoints.size() > 1 ? "⚠ " : "").append("« ").append(use.text).append(" »");
                use.copiesByPoints.forEach((points, list) -> line.append(" — ").append(points).append(" : ").append(String.join(", ", list)));
                w.line(line.toString());
            }
            w.line("");
        }));
    }

    // TEXTS OF THE COPY

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> texts(Map<String, Object> base){
        ArrayList<Map<String, Object>> all = new ArrayList<>();
        if(!(base.get("texts") instanceof Map<?, ?> pages)) return all;
        // In the order of the pages, then from the top
        TreeMap<Integer, List<?>> sorted = new TreeMap<>();
        pages.forEach((page, list) -> {
            if(list instanceof List<?> l) sorted.put(num(String.valueOf(page).replaceFirst("page", ""), 0).intValue(), l);
        });
        sorted.forEach((page, list) -> {
            List<Map<String, Object>> onPage = new ArrayList<>();
            for(Object t : list) if(t instanceof Map<?, ?> m) onPage.add(new LinkedHashMap<>((Map<String, Object>) m));
            onPage.forEach(t -> t.put("page", page));
            onPage.sort(Comparator.comparingDouble(t -> num(t.get("y"), 0)));
            all.addAll(onPage);
        });
        return all;
    }
    private static boolean isMark(Map<String, Object> t, String label){
        String text = str(t.get("text"));
        return t.get("pnfExercise") != null || text.equals(label) || text.startsWith(label + " : ");
    }
    // The table of the presentation marks on the first page (its header and its rows)
    private static boolean isTable(Map<String, Object> t, String label){
        if(num(t.get("page"), -1) != 0 || t.get("correctionLine") != null || t.get("pnfExercise") != null) return false;
        String text = str(t.get("text"));
        return Math.abs(num(t.get("x"), 0) - 3199) < 1000 && (text.equals(label) || text.matches("^\\d+\\.\\s.*|^\\d+\\.$"));
    }
    private static String scoredText(Map<String, Object> t, Writer w){
        if(t.get("scoredCommentId") == null) return str(t.get("text")).replace('\n', ' ').strip();
        String comment = str(t.get("comment")).replace('\n', ' ').strip();
        if(comment.contains("checkmark")) return w.tr("fairness.allPoints");
        if(Boolean.TRUE.equals(t.get("noPoints"))) return comment;
        String points = LineGrading.formatPoints(num(t.get("points"), 0), w.format);
        return comment.isEmpty() ? points : comment + " " + points;
    }

    // GRADES OF A COPY

    private static final class Grades {
        final Map<String, Object> root;
        final Map<String, List<Map<String, Object>>> children = new HashMap<>();
        @SuppressWarnings("unchecked")
        Grades(Map<String, Object> base){
            Map<String, Object> top = null;
            if(base.get("grades") instanceof List<?> list){
                for(Object o : list){
                    if(!(o instanceof Map<?, ?> m)) continue;
                    Map<String, Object> grade = (Map<String, Object>) m;
                    if(str(grade.get("parentPath")).isEmpty()) top = grade;
                    else children.computeIfAbsent(str(grade.get("parentPath")), k -> new ArrayList<>()).add(grade);
                }
            }
            children.values().forEach(l -> l.sort(Comparator.comparingDouble(g -> num(g.get("index"), 0))));
            root = top;
        }
        List<Map<String, Object>> children(Map<String, Object> grade){
            return children.getOrDefault(path(grade), List.of());
        }
        List<Map<String, Object>> leaves(Map<String, Object> grade){
            List<Map<String, Object>> kids = children(grade);
            if(kids.isEmpty()) return List.of(grade);
            List<Map<String, Object>> leaves = new ArrayList<>();
            for(Map<String, Object> kid : kids) leaves.addAll(leaves(kid));
            return leaves;
        }
    }

    // UTILS

    private static final class Writer {
        final StringBuilder text = new StringBuilder();
        final NumberFormat format;
        final Function<String, String> tr;
        Writer(NumberFormat format, Function<String, String> tr){
            this.format = format;
            this.tr = tr;
        }
        void line(String line){
            text.append(line).append('\n');
        }
        String tr(String key, String... args){
            String value = tr.apply(key);
            for(int i = 0; i < args.length; i++) value = value.replace("{" + i + "}", args[i]);
            return value;
        }
        String num(double value){
            return format.format(value);
        }
        String grade(Map<String, Object> grade){
            double value = value(grade);
            return (value < 0 ? "–" : num(value)) + " / " + num(total(grade));
        }
        String text(){
            return text.toString();
        }
    }
    private static String path(Map<String, Object> grade){
        return str(grade.get("parentPath")) + "\\" + str(grade.get("name"));
    }
    private static String name(Map<String, Object> grade){
        return str(grade.get("name"));
    }
    private static double value(Map<String, Object> grade){
        return num(grade.get("value"), -1);
    }
    private static double total(Map<String, Object> grade){
        return num(grade.get("total"), 0);
    }
    private static String str(Object value){
        return value == null ? "" : String.valueOf(value);
    }
    private static Double num(Object value, double defaultValue){
        if(value instanceof Number number) return number.doubleValue();
        try{
            return Double.parseDouble(String.valueOf(value));
        }catch(NumberFormatException e){
            return defaultValue;
        }
    }
}
