/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.PNFAnnotationManager;
import corrigo.panel.sidebar.grades.QuickGradePlacement;
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
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
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
    private static final LongProperty revision = new SimpleLongProperty();

    private LineCorrection(){
    }

    public static boolean isEnabled(){
        return Main.settings != null && Main.settings.gradingByLines.getValue();
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
        fire();
    }
    public static void load(Object data){
        entries.clear();
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

    public static String getText(String exercisePath){
        Entry entry = entries.get(exercisePath);
        return entry == null ? "" : entry.text;
    }

    // The text of the exercise was edited: ids, grades of its sub-questions, texts written on the copy.
    public static void setText(GradeTreeItem exercise, String text){
        if(exercise == null || !MainWindow.mainScreen.hasDocument(false)) return;
        String path = exercise.getCore().getPath();
        Entry entry = entries.computeIfAbsent(path, k -> new Entry());
        if(text.equals(entry.text)) return;
        Set<String> oldIds = new HashSet<>(entry.ids);
        entry.ids = LineIds.update(split(entry.text), entry.ids, split(text));
        entry.text = text;
        applyGrades(exercise, entry);
        reconcile(exercise, entry, oldIds);
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
    // A value typed by hand: not the one the lines gave last (a grade there before the lines counts as typed)
    private static boolean isTyped(double value, Double computed){
        return computed == null ? value >= 0 : Math.abs(value - computed) > EPS;
    }
    static Double gradeOf(List<Block> blocks, int leaf, double total){
        List<Line> lines = new ArrayList<>();
        for(Block block : blocks) if(block.leaf() == leaf) lines.addAll(block.lines());
        return lines.isEmpty() ? null : LineGrading.grade(lines, total);
    }

    // What the panel shows for a sub-grade: the grade of its lines (null: not graded), and whether a typed value wins
    public record LeafState(Double linesGrade, Double start, boolean typed) {}
    public static LeafState getState(GradeTreeItem exercise, GradeTreeItem leaf){
        Entry entry = entries.get(exercise.getCore().getPath());
        if(entry == null) return new LeafState(null, null, false);
        List<Block> blocks = parse(exercise, entry);
        int index = getLeaves(exercise).indexOf(leaf);
        Double grade = gradeOf(blocks, index, leaf.getCore().getTotal());
        List<Line> lines = new ArrayList<>();
        for(Block block : blocks) if(block.leaf() == index) lines.addAll(block.lines());
        // Typed by hand: the lines do not change it (shown when the exercise has lines)
        boolean typed = !entry.text.isBlank() && isTyped(leaf.getCore().getValue(), entry.computed.get(leaf.getCore().getPath()));
        return new LeafState(grade, LineGrading.start(lines, leaf.getCore().getTotal()), typed);
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

    // The texts written by the lines of the exercise follow its text: updated, or removed with their line
    private static void reconcile(GradeTreeItem exercise, Entry entry, Set<String> oldIds){
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
                    removePlaced(element);
                    continue;
                }
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
            element = new TextElement(x, y, page.getPage(), true, getCopyText(line), getColor(line), getFont(), QuickGradePlacement.COLUMN_WIDTH);
            element.setCorrectionLine(pending.id());
            page.addElement(element, true, UType.ELEMENT);
            if(center) element.centerOnCoordinatesY();
        }
        Edition.setUnsave("LinePlaced");
        fire();
        return element;
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
            if(element != null && element.getPage() != null) corrigo.panel.sidebar.grades.tags.TagScoring.moveToFreeSpotNowOrLater(element, element.getPage());
        }
    }
    // The copy is closed: the lines of all its exercises not placed yet are written next to their grades
    public static void placeAllNextToGrades(){
        if(!MainWindow.mainScreen.hasDocument(false) || GradeTreeView.getTotal() == null) return;
        for(var child : List.copyOf(GradeTreeView.getTotal().getChildren())){
            if(child instanceof GradeTreeItem exercise && entries.containsKey(exercise.getCore().getPath())) placeAllNextToGrades(exercise);
        }
    }
    // Number of lines of the copy not placed yet, in all its exercises
    public static int countUnplaced(){
        if(GradeTreeView.getTotal() == null) return 0;
        int count = 0;
        for(var child : GradeTreeView.getTotal().getChildren()){
            if(child instanceof GradeTreeItem exercise) count += getUnplaced(exercise).size();
        }
        return count;
    }
    public static boolean hasUnplaced(){
        if(GradeTreeView.getTotal() == null) return false;
        for(var child : GradeTreeView.getTotal().getChildren()){
            if(child instanceof GradeTreeItem exercise && !getUnplaced(exercise).isEmpty()) return true;
        }
        return false;
    }

    private static javafx.scene.text.Font getFont(){
        int size = Main.settings == null ? 14 : Main.settings.commentsSize.getValue();
        return FontUtils.getFont("Open Sans", false, false, Math.clamp(size, 6, 60));
    }

    // OTHER COPIES

    private record CachedFile(long modified, Map<?, ?> corrections) {}
    private static final HashMap<java.io.File, CachedFile> otherCopies = new HashMap<>();

    // The texts of this exercise on the other copies of the evaluation folder (read again when their file changed)
    public static List<String> getOtherTexts(String exercisePath){
        ArrayList<String> texts = new ArrayList<>();
        if(!MainWindow.mainScreen.hasDocument(false)) return texts;
        java.io.File open = MainWindow.mainScreen.document.getFile().getAbsoluteFile();
        for(java.io.File pdf : corrigo.panel.sidebar.grades.tags.ExerciseTags.getFolderCopies()){
            if(pdf.getAbsoluteFile().equals(open)) continue;
            java.io.File editFile = Edition.getEditFile(pdf);
            if(!editFile.exists()) continue;
            CachedFile cached = otherCopies.get(editFile);
            if(cached == null || cached.modified() != editFile.lastModified()){
                Map<?, ?> corrections = Map.of();
                try{
                    fr.clementgre.pdf4teachers.datasaving.Config config = new fr.clementgre.pdf4teachers.datasaving.Config(editFile);
                    config.load();
                    if(config.base.get(KEY) instanceof Map<?, ?> map) corrections = map;
                }catch(Exception e){
                    fr.clementgre.pdf4teachers.interfaces.windows.log.Log.w("Unable to read the lines of " + editFile.getName() + ": " + e.getMessage());
                }
                cached = new CachedFile(editFile.lastModified(), corrections);
                otherCopies.put(editFile, cached);
            }
            if(cached.corrections().get(exercisePath) instanceof Map<?, ?> exercise && exercise.get("text") instanceof String text
                    && !text.isBlank()) texts.add(text);
        }
        return texts;
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
