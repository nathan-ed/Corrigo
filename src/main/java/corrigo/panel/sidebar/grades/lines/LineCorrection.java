/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.PNFAnnotationManager;
import corrigo.panel.sidebar.grades.QuickGradePlacement;
import corrigo.panel.sidebar.grades.tags.TagScoring;
import corrigo.panel.sidebar.grades.lines.LineGrading.Block;
import corrigo.panel.sidebar.grades.lines.LineGrading.Kind;
import corrigo.panel.sidebar.grades.lines.LineGrading.Line;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UndoEngine;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.paint.Color;

import java.util.*;

/**
 * The grading by lines of the open copy: for each exercise, the text typed (see LineGrading), the id of each line
 * (see LineIds) and the grade each sub-question got from its lines.
 * <ul>
 * <li>The grades only change when the text of the exercise is edited (never when the copy is opened).</li>
 * <li>A grade typed by hand stays: a sub-grade whose value is not the one the lines gave it last is not changed by the
 * lines, until the teacher asks for the grade of the lines again (resetToLines).</li>
 * <li>Each line is written on the copy once placed (a click, or next to its grade), as a text linked to the line by
 * its id: editing the line edits the text, deleting the line deletes it.</li>
 * </ul>
 * Saved in the edit file of the copy, in its own section (Edition).
 */
public final class LineCorrection {

    public static final String KEY = "corrections";
    private static final double EPS = 1e-9;

    public static final Color METHOD_COLOR = Color.web("#2e7d32"), MISTAKE_COLOR = Color.web("#c62828"), COMMENT_COLOR = Color.web("#1565c0");

    private static final class Entry {
        String text = "";
        List<String> ids = new ArrayList<>();
        // Grade given by the lines to each sub-grade, the last time they changed it
        final LinkedHashMap<String, Double> computed = new LinkedHashMap<>();
        boolean isEmpty(){
            return text.isBlank() && computed.isEmpty();
        }
    }
    // By path of exercise
    private static final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    // The texts of the copy whose lines were deleted (by path of exercise, the last one first): put back where they
    // were when an undo of the text brings their lines back (restoreRemoved)
    private record Removed(String line, int page, int x, int y) {}
    private static final HashMap<String, ArrayDeque<Removed>> removed = new HashMap<>();
    private static final LongProperty revision = new SimpleLongProperty();

    private LineCorrection(){
    }

    public static boolean isEnabled(){
        return Main.settings != null && Main.settings.gradingByLines.getValue();
    }
    // The lines not placed yet are written next to their grades when the copy is closed (setting; else a click each)
    public static boolean isAutoPlace(){
        return isEnabled() && Main.settings.linesAutoPlace.getValue();
    }
    public static LongProperty revisionProperty(){
        return revision;
    }
    private static void fire(){
        revision.set(revision.get() + 1);
    }
    // A text written by a line was added or removed on the copy (placed, undone, deleted)
    public static void onPlacedChanged(){
        fire();
    }

    // LOAD AND SAVE

    public static void clear(){
        entries.clear();
        removed.clear();
        fire();
    }
    public static void load(Object data){
        entries.clear();
        removed.clear();
        if(data instanceof Map<?, ?> map){
            for(Map.Entry<?, ?> exercise : map.entrySet()){
                if(!(exercise.getValue() instanceof Map<?, ?> values)) continue;
                Entry entry = new Entry();
                if(values.get("text") instanceof String text) entry.text = text;
                if(values.get("ids") instanceof List<?> ids) entry.ids = new ArrayList<>(ids.stream().map(id -> id == null ? "" : id.toString()).toList());
                if(values.get("computed") instanceof Map<?, ?> computed){
                    computed.forEach((path, value) -> {
                        if(value instanceof Number number) entry.computed.put(String.valueOf(path), number.doubleValue());
                    });
                }
                // Ids missing or not matching the lines (file edited by hand): made again, the texts placed lose their link
                List<String> lines = split(entry.text);
                if(entry.ids.size() != lines.size()) entry.ids = LineIds.update(List.of(), List.of(), lines);
                entries.put(String.valueOf(exercise.getKey()), entry);
            }
        }
        fire();
    }
    public static boolean isEmpty(){
        return entries.values().stream().allMatch(Entry::isEmpty);
    }
    public static LinkedHashMap<String, Object> toYAML(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        entries.forEach((path, entry) -> {
            if(entry.isEmpty()) return;
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("text", entry.text);
            values.put("ids", new ArrayList<>(entry.ids));
            values.put("computed", new LinkedHashMap<>(entry.computed));
            data.put(path, values);
        });
        return data;
    }

    // TEXT AND GRADES

    // The texts of the exercises of the open copy (exercise path → text)
    public static Map<String, String> getTexts(){
        LinkedHashMap<String, String> texts = new LinkedHashMap<>();
        entries.forEach((path, entry) -> { if(!entry.text.isBlank()) texts.put(path, entry.text); });
        return texts;
    }
    public static String getText(String exercisePath){
        Entry entry = entries.get(exercisePath);
        return entry == null ? "" : entry.text;
    }

    // The text of the exercise was edited: ids, grades of its sub-questions, texts written on the copy.
    public static void setText(GradeTreeItem exercise, String text){
        setText(exercise, text, null);
    }
    // An edit of the text: the part [start, end[ of the old text replaced by inserted
    public record TextEdit(int start, int end, String inserted) {}
    // edit: what was edited, if known (null: compared as a whole); the lines out of it keep their ids
    public static void setText(GradeTreeItem exercise, String text, TextEdit edit){
        if(exercise == null || !MainWindow.mainScreen.hasDocument(false)) return;
        String path = exercise.getCore().getPath();
        Entry entry = entries.computeIfAbsent(path, k -> new Entry());
        if(text.equals(entry.text)) return;
        Set<String> oldIds = new HashSet<>(entry.ids);
        Map<String, String> oldLines = linesById(entry.text, entry.ids);
        int[] kept = edit == null ? new int[]{0, 0} : linesOutside(entry.text, edit);
        entry.ids = LineIds.update(split(entry.text), entry.ids, split(text), kept[0], kept[1]);
        entry.text = text;
        applyGrades(exercise, entry);
        PNFAnnotationManager.onGradingChanged(); // An empty presentation grade is filled once the copy is graded
        reconcile(exercise, entry, oldIds, oldLines);
        Edition.setUnsave("LinesEdited");
        fire();
    }

    private static void applyGrades(GradeTreeItem exercise, Entry entry){
        List<GradeTreeItem> leaves = getLeaves(exercise);
        List<Block> blocks = parse(exercise, entry);
        for(int i = 0; i < leaves.size(); i++){
            GradeElement leaf = leaves.get(i).getCore();
            Double grade = gradeOf(blocks, i, leaf.getTotal());
            String leafPath = leaf.getPath();
            Double computed = entry.computed.get(leafPath);
            // Typed by hand: stays, whatever the lines give (even the same value while a line is being typed), until
            // the teacher asks for the grade of the lines again (resetToLines)
            if(isTyped(leaf.getValue(), computed)) continue;
            if(grade == null){
                if(computed != null){ // Its lines do not count any more: not graded
                    entry.computed.remove(leafPath);
                    leaf.setAutomaticValue(-1);
                }
                continue;
            }
            entry.computed.put(leafPath, grade);
            if(Math.abs(leaf.getValue() - grade) > EPS) leaf.setAutomaticValue(grade);
        }
    }
    // The number of lines of the text not touched by the edit: before it (their line break before its start) and
    // after it (starting after its end; the one starting at its end only if a line break is still before it)
    static int[] linesOutside(String text, TextEdit edit){
        String inserted = edit.inserted();
        boolean apart = inserted.isEmpty() ? edit.start() == 0 || text.charAt(edit.start() - 1) == '\n' : inserted.endsWith("\n");
        int before = 0, after = edit.end() == 0 && apart ? 1 : 0;
        for(int i = 0; i < text.length(); i++){
            if(text.charAt(i) != '\n') continue;
            if(i < edit.start()) before++;
            if(i + 1 > edit.end() || i + 1 == edit.end() && apart) after++;
        }
        return new int[]{before, after};
    }
    // A value typed by hand: not the one the lines gave last (a grade there before the lines counts as typed)
    private static boolean isTyped(double value, Double computed){
        return computed == null ? value >= 0 : Math.abs(value - computed) > EPS;
    }
    // The grade of the sub-question at leaf from its lines (null: no line, not graded)
    private static Double gradeOf(List<Block> blocks, int leaf, double total){
        List<Line> lines = LineGrading.linesOf(blocks, leaf);
        return lines.isEmpty() ? null : LineGrading.grade(lines, total);
    }

    // What the panel shows for a sub-grade: the grade of its lines (null: not graded), and whether a typed value wins
    public record LeafState(Double linesGrade, Double start, boolean typed) {}
    public static LeafState getState(GradeTreeItem exercise, GradeTreeItem leaf){
        Entry entry = entries.get(exercise.getCore().getPath());
        if(entry == null) return new LeafState(null, null, false);
        List<Block> blocks = parse(exercise, entry);
        int index = getLeaves(exercise).indexOf(leaf);
        double total = leaf.getCore().getTotal();
        // Typed by hand: the lines do not change it (shown when the exercise has lines)
        boolean typed = !entry.text.isBlank() && isTyped(leaf.getCore().getValue(), entry.computed.get(leaf.getCore().getPath()));
        return new LeafState(gradeOf(blocks, index, total), LineGrading.start(LineGrading.linesOf(blocks, index), total), typed);
    }
    // The teacher asks for the grade of the lines again, over a value typed by hand
    public static void resetToLines(GradeTreeItem exercise, GradeTreeItem leaf){
        Entry entry = entries.computeIfAbsent(exercise.getCore().getPath(), k -> new Entry());
        int index = getLeaves(exercise).indexOf(leaf);
        Double grade = gradeOf(parse(exercise, entry), index, leaf.getCore().getTotal());
        String leafPath = leaf.getCore().getPath();
        if(grade == null){
            entry.computed.remove(leafPath);
            leaf.getCore().setAutomaticValue(-1);
        }else{
            entry.computed.put(leafPath, grade);
            leaf.getCore().setAutomaticValue(grade);
        }
        Edition.setUnsave("LinesReset");
        fire();
    }

    // The blocks of the text of the exercise (with the ids of their lines: entry.ids.get(line.index()))
    public static List<Block> getBlocks(GradeTreeItem exercise){
        Entry entry = entries.get(exercise.getCore().getPath());
        return entry == null ? List.of() : parse(exercise, entry);
    }
    private static List<Block> parse(GradeTreeItem exercise, Entry entry){
        return LineGrading.parse(entry.text, getLeaves(exercise).stream().map(leaf -> leaf.getCore().getName()).toList(),
                PNFAnnotationManager.label());
    }
    public static List<GradeTreeItem> getLeaves(GradeTreeItem exercise){
        if(!exercise.hasSubGrade()) return List.of(exercise);
        return GradeTreeView.getGradesArray(exercise).stream().filter(item -> !item.hasSubGrade()).toList();
    }
    static List<String> split(String text){
        return Arrays.asList(text.split("\\R", -1));
    }

    // TEXTS ON THE COPY

    // What a line writes on the copy
    public static String getCopyText(Line line){
        if(line.kind() == Kind.PNF) return PNFAnnotationManager.getText(line.comment());
        return LineGrading.render(line, MainWindow.gradesDigFormat, Main.settings == null || Main.settings.linesShowPoints.getValue());
    }
    public static Color getColor(Line line){
        return switch(line.kind()){
            case METHOD, FULL -> METHOD_COLOR;
            case MISTAKE, ZERO -> MISTAKE_COLOR;
            default -> COMMENT_COLOR;
        };
    }

    private static List<TextElement> getPlacedElements(){
        ArrayList<TextElement> placed = new ArrayList<>();
        if(!MainWindow.mainScreen.hasDocument(false)) return placed;
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            for(Element element : page.getElements()){
                if(element instanceof TextElement text && text.getCorrectionLine() != null) placed.add(text);
            }
        }
        return placed;
    }

    // The text of each line by its id
    private static Map<String, String> linesById(String text, List<String> ids){
        HashMap<String, String> byId = new HashMap<>();
        List<String> lines = split(text);
        for(int i = 0; i < Math.min(lines.size(), ids.size()); i++) if(!ids.get(i).isEmpty()) byId.put(ids.get(i), lines.get(i));
        return byId;
    }
    // The texts written by the lines of the exercise follow its text: removed with their line, written again when their
    // line changed. A text whose line did not change stays as it is on the copy (as it was converted from an older
    // version, or with a note in parentheses written before)
    private static void reconcile(GradeTreeItem exercise, Entry entry, Set<String> oldIds, Map<String, String> oldLines){
        Map<String, String> newLines = linesById(entry.text, entry.ids);
        HashMap<String, Line> lines = new HashMap<>();
        for(Block block : parse(exercise, entry)){
            for(Line line : block.lines()) lines.put(entry.ids.get(line.index()), line);
        }
        withoutUndo(() -> {
            for(TextElement element : getPlacedElements()){
                String id = element.getCorrectionLine();
                if(!oldIds.contains(id) && !lines.containsKey(id)) continue; // Another exercise
                Line line = lines.get(id);
                boolean isMark = PNFAnnotationManager.isMark(element);
                if(line == null || !line.isWritten() || (line.kind() == Kind.PNF) != isMark){
                    String deleted = oldLines.get(id);
                    if(line == null && deleted != null && !deleted.isBlank()){ // Its line deleted: kept to put it back
                        removed.computeIfAbsent(exercise.getCore().getPath(), k -> new ArrayDeque<>())
                                .push(new Removed(deleted.strip(), element.getPageNumber(), element.getRealX(), element.getRealY()));
                    }
                    removePlaced(element);
                    continue;
                }
                if(Objects.equals(oldLines.get(id), newLines.get(id))) continue; // This line did not change
                String text = getCopyText(line);
                if(!text.equals(element.getText())) element.setText(text);
                if(line.kind() != Kind.PNF && !getColor(line).equals(element.getColor())) element.setColor(getColor(line));
            }
        });
    }
    private static void removePlaced(TextElement element){
        String exercise = element.getPnfExercise();
        if(exercise != null){ // Its bar of the table of the first page goes with it
            element.setPnfExercise(null);
            PNFAnnotationManager.removeMarkFromTable(exercise, UType.NO_UNDO);
        }
        element.delete(true, UType.NO_UNDO);
    }
    private static void withoutUndo(Runnable action){
        UndoEngine engine = MainWindow.mainScreen.getUndoEngine();
        UType previous = engine == null ? null : engine.getOverrideUndoType();
        if(engine != null) engine.setOverrideUndoType(UType.NO_UNDO);
        try{
            action.run();
        }finally{
            if(engine != null) engine.setOverrideUndoType(previous);
        }
    }

    // PLACEMENT

    // A line to write on the copy, not placed yet. leaf: index of its sub-question (-1: extra block)
    public record Pending(String id, Line line, int leaf) {}

    // The lines of the exercise not written on the copy yet, in the order of the text
    public static List<Pending> getUnplaced(GradeTreeItem exercise){
        Entry entry = exercise == null ? null : entries.get(exercise.getCore().getPath());
        if(entry == null) return List.of();
        Set<String> placed = new HashSet<>();
        for(TextElement element : getPlacedElements()) placed.add(element.getCorrectionLine());
        ArrayList<Pending> pending = new ArrayList<>();
        for(Block block : parse(exercise, entry)){
            for(Line line : block.lines()){
                String id = entry.ids.get(line.index());
                if(line.isWritten() && !placed.contains(id)) pending.add(new Pending(id, line, block.leaf()));
            }
        }
        pending.sort(Comparator.comparingInt(p -> p.line().index()));
        return pending;
    }

    // Writes the line at this place of the page (grid coordinates; center: the place is the vertical center of the text)
    public static TextElement place(GradeTreeItem exercise, Pending pending, PageRenderer page, int x, int y, boolean center){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        Line line = pending.line();
        TextElement element;
        if(line.kind() == Kind.PNF){
            int index = GradeTreeView.getTotal().getChildren().indexOf(exercise);
            element = PNFAnnotationManager.addPNFAtGrid(page, x, y, line.comment(), index);
            if(element == null) return null;
            element.setCorrectionLine(pending.id());
        }else{
            element = new TextElement(x, y, page.getPage(), true, getCopyText(line), getColor(line), PNFAnnotationManager.getCommentFont(), QuickGradePlacement.COLUMN_WIDTH);
            element.setCorrectionLine(pending.id());
            page.addElement(element, true, UType.ELEMENT);
            if(center) element.centerOnCoordinatesY();
        }
        if(!center){ // The place given is its own (put back after an undo, next to the grades): exactly, not rounded
            TextElement placed = element;
            withoutUndo(() -> {
                placed.setRealX(x);
                placed.setRealY(y);
            });
        }
        Edition.setUnsave("LinePlaced");
        fire();
        return element;
    }
    // An undo of the text brought back lines deleted before: their texts are written again where they were
    public static void restoreRemoved(GradeTreeItem exercise){
        ArrayDeque<Removed> deleted = exercise == null ? null : removed.get(exercise.getCore().getPath());
        if(deleted == null || deleted.isEmpty() || !MainWindow.mainScreen.hasDocument(false)) return;
        for(Pending pending : getUnplaced(exercise)){
            Removed match = deleted.stream().filter(r -> r.line().equals(pending.line().raw().strip())).findFirst().orElse(null);
            if(match == null || match.page() >= MainWindow.mainScreen.document.getPagesNumber()) continue;
            deleted.remove(match);
            place(exercise, pending, MainWindow.mainScreen.document.getPage(match.page()), match.x(), match.y(), false);
        }
    }
    // Writes the lines not placed yet next to the grade of their sub-question
    public static void placeAllNextToGrades(GradeTreeItem exercise){
        if(exercise == null || exercise.isDeleted() || !MainWindow.mainScreen.hasDocument(false)) return;
        List<GradeTreeItem> leaves = getLeaves(exercise);
        int index = GradeTreeView.getTotal().getChildren().indexOf(exercise);
        OptionalInt mapped = index < 0 ? OptionalInt.empty() : MainWindow.footerBar.getExercisePage(index);
        int fallbackPage = mapped.orElse(0);
        for(Pending pending : getUnplaced(exercise)){
            GradeElement anchor = pending.leaf() >= 0 && pending.leaf() < leaves.size() ? leaves.get(pending.leaf()).getCore() : exercise.getCore();
            QuickGradePlacement.Spot spot = QuickGradePlacement.nextSpot(anchor, fallbackPage);
            TextElement element = place(exercise, pending, spot.page(), spot.x(), spot.y(), false);
            // Not over what the student wrote nor over another annotation: the free place the closest
            if(element != null && element.getPage() != null) TagScoring.moveToFreeSpotNowOrLater(element, element.getPage());
        }
    }
    // The copy is closed: the lines of all its exercises not placed yet are written next to their grades
    public static void placeAllNextToGrades(){
        if(!MainWindow.mainScreen.hasDocument(false) || GradeTreeView.getTotal() == null) return;
        for(var child : List.copyOf(GradeTreeView.getTotal().getChildren())){
            if(child instanceof GradeTreeItem exercise && entries.containsKey(exercise.getCore().getPath())) placeAllNextToGrades(exercise);
        }
    }
    // Number of lines of the copy not placed yet, by name of exercise (only the exercises that have some)
    public static LinkedHashMap<String, Integer> countUnplaced(){
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        if(GradeTreeView.getTotal() == null) return counts;
        for(var child : GradeTreeView.getTotal().getChildren()){
            if(!(child instanceof GradeTreeItem exercise)) continue;
            int count = getUnplaced(exercise).size();
            if(count > 0) counts.put(exercise.getCore().getName(), count);
        }
        return counts;
    }

    // RENAME

    // A grade was renamed: the exercises and sub-grades of the texts follow
    public static void onGradeRenamed(String oldPath, String newPath){
        if(oldPath.equals(newPath)) return;
        LinkedHashMap<String, Entry> renamed = new LinkedHashMap<>();
        entries.forEach((path, entry) -> {
            LinkedHashMap<String, Double> computed = new LinkedHashMap<>();
            entry.computed.forEach((leaf, value) -> computed.put(rename(leaf, oldPath, newPath), value));
            entry.computed.clear();
            entry.computed.putAll(computed);
            renamed.put(rename(path, oldPath, newPath), entry);
        });
        entries.clear();
        entries.putAll(renamed);
        fire();
    }
    private static String rename(String path, String oldPath, String newPath){
        if(path.equals(oldPath)) return newPath;
        if(path.startsWith(oldPath + "\\")) return newPath + path.substring(oldPath.length());
        return path;
    }
}
