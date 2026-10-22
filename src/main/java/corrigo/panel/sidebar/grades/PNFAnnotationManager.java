/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PNFAnnotationManager {
    
    private static final Color PNF_COLOR = Color.web("#b31a1a");
    private static final Font PNF_FONT = FontUtils.getFont("Open Sans", false, false, 24);
    private static final Font PNF_COMMENT_FONT = FontUtils.getFont("Open Sans", false, false, 18);
    private static final Font PNF_TABLE_FONT = FontUtils.getFont("Open Sans", false, false, 18);
    private static final String DEFAULT_LABEL = "PNF";
    private static final String PNF_MARK = "I";
    static final int MAX_MARKS_PER_EXERCISE = 4;
    static final int TABLE_X = 3199;
    static final int TABLE_HEADER_Y = 130459;
    static final int TABLE_ROW_STEP = (int) (Element.GRID_HEIGHT * .035);
    
    private PNFAnnotationManager(){
    }
    
    // The name of the marks, chosen in the settings ("PNF", "Présentation"...)
    public static String label(){
        String name = Main.settings == null ? null : Main.settings.presentationName.getValue();
        return name == null || name.isBlank() ? DEFAULT_LABEL : name.trim();
    }
    
    public static void addPNF(PageRenderer page, double pageX, double pageY){
        addPNF(page, pageX, pageY, null);
    }
    // A mark at this place of the page (pixels), with a comment or not: "PNF : units missing".
    // It is counted for the exercise being graded in the table of the first page (not counted there without exercise).
    public static void addPNF(PageRenderer page, double pageX, double pageY, String comment){
        addPNFAtGrid(page, page.toGridX(pageX), page.toGridY(pageY), comment);
    }
    public static TextElement addPNFAtGrid(PageRenderer page, int gridX, int gridY, String comment){
        return addPNFAtGrid(page, gridX, gridY, comment, MainWindow.footerBar.getSelectedExerciseIndex());
    }
    // A mark counted for the exercise at this index of the grade scale (-1: not counted)
    public static TextElement addPNFAtGrid(PageRenderer page, int gridX, int gridY, String comment, int topLevelIndex){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        
        List<String> exercises = getExerciseNames();
        int exerciseIndex = getRowIndex(exercises, topLevelIndex);
        int rowCount = getRowCount(exercises);
        
        // All the changes below are registered as a single undo action.
        UndoGroup undoGroup = new UndoGroup();
        if(exerciseIndex >= 0) incrementSummaryMark(exerciseIndex, rowCount, undoGroup);
        TextElement element = addPNFAnnotation(page, gridX, gridY, comment, undoGroup);
        // Counted for this exercise: deleting the mark removes its bar (set after it is added, which already counted it)
        if(exerciseIndex >= 0) element.setPnfExercise(exercises.get(topLevelIndex));
        rememberComment(comment);
        Edition.setUnsave("PNF annotation added");
        MainWindow.mainScreen.document.edition.save(false);
        return element;
    }
    
    // A mark counted in the table of the first page was deleted by the teacher: its bar is removed, in the same undo
    // step (an undo or a redo puts back the table itself).
    public static void onCountedMarkDeleted(String exercise){
        if(MainWindow.mainScreen.getUndoEngine() == null || MainWindow.mainScreen.getUndoEngine().isUndoingThings()) return;
        removeMarkFromTable(exercise, UType.ELEMENT_NO_COUNT_AFTER);
    }
    // Removes a bar of this exercise from the table of the first page (undoType: of the change of the row)
    public static void removeMarkFromTable(String exercise, UType undoType){
        if(!MainWindow.mainScreen.hasDocument(false) || exercise == null) return;
        List<String> exercises = getExerciseNames();
        int index = exercises.indexOf(exercise);
        int row = index < 0 ? -1 : getRowIndex(exercises, index);
        if(row < 0) return;
        ArrayList<TextElement> rows = getSummaryRows(MainWindow.mainScreen.document.getPage(0));
        if(row >= rows.size() || countMarks(rows.get(row).getText()) == 0) return;
        // The table shows MAX_MARKS_PER_EXERCISE bars at most: with as many marks left, it does not change
        long left = MainWindow.mainScreen.document.getPages().stream().flatMap(page -> page.getElements().stream())
                .filter(element -> element instanceof TextElement text && exercise.equals(text.getPnfExercise())).count();
        if(left >= MAX_MARKS_PER_EXERCISE) return;
        var engine = MainWindow.mainScreen.getUndoEngine();
        UType previous = engine == null ? null : engine.getOverrideUndoType();
        if(engine != null) engine.setOverrideUndoType(undoType);
        try{
            rows.get(row).setText(removeMarkFromRowText(rows.get(row).getText(), row));
        }finally{
            if(engine != null) engine.setOverrideUndoType(previous);
        }
    }
    static String removeMarkFromRowText(String text, int rowIndex){
        int marks = countMarks(text);
        if(marks == 0) return text;
        String label = getRowLabel(rowIndex);
        return marks == 1 ? label : label + String.join("  ", java.util.Collections.nCopies(marks - 1, PNF_MARK));
    }
    
    // TEXT AND COMMENTS
    
    public static String getText(String comment){
        return comment == null || comment.isBlank() ? label() : label() + " : " + comment.strip();
    }
    // The comments used before, the last used first
    public static List<String> getComments(){
        return MainWindow.userData == null ? List.of() : List.copyOf(MainWindow.userData.presentationComments);
    }
    private static final int MAX_COMMENTS = 20;
    private static void rememberComment(String comment){
        if(comment == null || comment.isBlank() || MainWindow.userData == null) return;
        List<String> comments = MainWindow.userData.presentationComments;
        comments.removeIf(c -> c.equalsIgnoreCase(comment.strip()));
        comments.addFirst(comment.strip());
        while(comments.size() > MAX_COMMENTS) comments.removeLast();
    }
    public static void forgetComment(String comment){
        if(MainWindow.userData != null) MainWindow.userData.presentationComments.remove(comment);
    }
    
    // The choice of the comment: none, one used before (✕ forgets it), or a new one typed in the menu
    public static List<javafx.scene.control.MenuItem> buildCommentItems(java.util.function.Consumer<String> onChosen){
        ArrayList<javafx.scene.control.MenuItem> items = new ArrayList<>();
        javafx.scene.control.MenuItem none = new javafx.scene.control.MenuItem(TR.tr("pnf.comment.none", label()));
        none.setOnAction(e -> onChosen.accept(null));
        items.add(none);
        for(String comment : getComments()){
            javafx.scene.control.Label text = new javafx.scene.control.Label(getText(comment));
            text.setMaxWidth(320);
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            javafx.scene.control.Label forget = new javafx.scene.control.Label("✕");
            forget.setStyle("-fx-opacity: .6; -fx-padding: 0 4;");
            forget.setCursor(javafx.scene.Cursor.HAND);
            javafx.scene.control.Tooltip.install(forget, new javafx.scene.control.Tooltip(TR.tr("pnf.comment.forget")));
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(12, text, spacer, forget);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setPadding(new javafx.geometry.Insets(0, 0, 0, 13)); // Aligned with the text of the other items
            javafx.scene.control.CustomMenuItem item = new javafx.scene.control.CustomMenuItem(row);
            forget.setOnMousePressed(e -> {
                e.consume();
                forgetComment(comment);
                if(item.getParentPopup() != null) item.getParentPopup().getItems().remove(item);
                else if(item.getParentMenu() != null) item.getParentMenu().getItems().remove(item);
            });
            item.setOnAction(e -> onChosen.accept(comment));
            items.add(item);
        }
        items.add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.TextField field = new javafx.scene.control.TextField();
        field.setPromptText(TR.tr("pnf.comment.new"));
        field.setPrefColumnCount(22);
        javafx.scene.control.CustomMenuItem newItem = new javafx.scene.control.CustomMenuItem(field, false);
        field.setOnAction(e -> {
            if(field.getText().isBlank()) return;
            String comment = field.getText().strip();
            if(newItem.getParentPopup() != null) newItem.getParentPopup().hide();
            onChosen.accept(comment);
        });
        items.add(newItem);
        return items;
    }
    
    // POINTS
    
    // Points of the presentation for this number of marks on the copy: 0-1 → 2, 2 → 1.5, 3 → 1, 4 → 0.5, 5 or more → 0
    public static double score(int marks){
        return Math.max(0, 2 - .5 * Math.max(0, marks - 1));
    }
    // The marks written on the copy (not the table of the first page)
    static boolean isMarkText(String text, String label){
        return text != null && (text.equals(label) || text.startsWith(label + " : "));
    }
    public static boolean isMark(Element element){
        if(!(element instanceof TextElement text) || !isMarkText(text.getText(), label())) return false;
        return !(element.getPageNumber() == 0 && Math.abs(element.getRealX() - TABLE_X) < 1000 && Math.abs(element.getRealY() - TABLE_HEADER_Y) < 1000);
    }
    public static int countCopyMarks(){
        if(!MainWindow.mainScreen.hasDocument(false)) return 0;
        int count = 0;
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            for(Element element : page.getElements()) if(isMark(element)) count++;
        }
        return count;
    }
    // The grade of the presentation (an exercise named like the marks, without sub-grades), or null
    private static GradeTreeItem getPresentationGrade(){
        if(GradeTreeView.getTotal() == null) return null;
        for(var child : GradeTreeView.getTotal().getChildren()){
            if(child instanceof GradeTreeItem item && isPNFExercise(item.getCore().getName()) && !item.hasSubGrade()) return item;
        }
        return null;
    }
    private static boolean scoreScheduled;
    // A mark was added or removed: the grade of the presentation is computed again (once for several changes)
    public static void onMarksChanged(){
        if(scoreScheduled) return;
        scoreScheduled = true;
        javafx.application.Platform.runLater(() -> {
            scoreScheduled = false;
            updateScore(true);
            corrigo.panel.sidebar.grades.tags.ExerciseTags.fireChanged(false); // The count on the card
        });
    }
    // The copy was opened: an empty presentation grade is filled from its marks once its grading has started. A grade
    // already there (typed by hand or computed before) is never changed by the opening.
    public static void onEditionLoaded(){
        updateScoreWhenReady(50);
    }
    // Once the document is ready (its undo engine is set after the edition is loaded)
    private static void updateScoreWhenReady(int tries){
        javafx.application.Platform.runLater(() -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            if(MainWindow.mainScreen.getUndoEngine() == null){
                if(tries > 0) updateScoreWhenReady(tries - 1);
                return;
            }
            updateScore(false);
        });
    }
    private static void updateScore(boolean changed){
        if(Main.settings == null || !Main.settings.presentationMarks.getValue() || !MainWindow.mainScreen.hasDocument(false)) return;
        GradeTreeItem grade = getPresentationGrade();
        if(grade == null) return;
        int marks = countCopyMarks();
        if(!changed && (grade.getCore().getValue() >= 0 || marks == 0 && !isGradingStarted(grade))) return;
        double value = Math.min(score(marks), grade.getCore().getTotal());
        if(grade.getCore().getValue() != value) grade.getCore().setAutomaticValue(value);
    }
    private static boolean isGradingStarted(GradeTreeItem presentation){
        return GradeTreeView.getGradesArray(GradeTreeView.getTotal()).stream()
                .anyMatch(item -> item != presentation && !item.hasSubGrade() && item.getCore().getValue() >= 0);
    }
    
    // Number of marks of the exercise being graded on the open copy (its row of the table of the first page)
    public static int countForSelectedExercise(){
        if(!MainWindow.mainScreen.hasDocument(false) || MainWindow.footerBar == null) return 0;
        int row = getRowIndex(getExerciseNames(), MainWindow.footerBar.getSelectedExerciseIndex());
        if(row < 0) return 0;
        ArrayList<TextElement> rows = getSummaryRows(MainWindow.mainScreen.document.getPage(0));
        return row < rows.size() ? countMarks(getMarksText(rows.get(row).getText())) : 0;
    }
    
    // The first action of the group starts a new undo step, the following ones are merged into it.
    private static class UndoGroup {
        private boolean started;
        UType next(){
            if(started) return UType.ELEMENT_NO_COUNT_BEFORE;
            started = true;
            return UType.ELEMENT;
        }
    }
    
    private static TextElement addPNFAnnotation(PageRenderer page, int gridX, int gridY, String comment, UndoGroup undoGroup){
        boolean commented = comment != null && !comment.isBlank();
        TextElement element = new TextElement(gridX, gridY, page.getPage(),
                true, getText(comment), PNF_COLOR, commented ? PNF_COMMENT_FONT : PNF_FONT, commented ? 40 : 0);
        page.addElement(element, true, undoGroup.next());
        element.centerOnCoordinatesY();
        return element;
    }
    
    private static void incrementSummaryMark(int exerciseIndex, int rowCount, UndoGroup undoGroup){
        PageRenderer firstPage = MainWindow.mainScreen.document.getPage(0);
        ArrayList<TextElement> rows = getSummaryRows(firstPage);
        
        // Existing row: edit it first, as a text edit always registers a new undo step.
        if(exerciseIndex < rows.size()){
            undoGroup.next();
            TextElement row = rows.get(exerciseIndex);
            row.setText(addMarkToRowText(row.getText(), exerciseIndex));
        }
        
        if(getSummaryHeader(firstPage).isEmpty()){
            TextElement header = new TextElement(TABLE_X, TABLE_HEADER_Y, 0, true, label(), PNF_COLOR, PNF_TABLE_FONT, 0);
            firstPage.addElement(header, true, undoGroup.next());
        }
        for(int i = rows.size(); i < rowCount; i++){
            String text = i == exerciseIndex ? addMarkToRowText(getRowLabel(i), i) : getRowLabel(i);
            TextElement row = new TextElement(TABLE_X, getRowY(i), 0, true, text, PNF_COLOR, PNF_TABLE_FONT, 0);
            firstPage.addElement(row, true, undoGroup.next());
        }
    }
    
    private static Optional<TextElement> getSummaryHeader(PageRenderer firstPage){
        return firstPage.getElements().stream()
                .filter(TextElement.class::isInstance)
                .map(TextElement.class::cast)
                .filter(element -> element.getPageNumber() == 0)
                .filter(element -> label().equals(element.getText()))
                .filter(element -> Math.abs(element.getRealX() - TABLE_X) < 1000)
                .filter(element -> Math.abs(element.getRealY() - TABLE_HEADER_Y) < 1000)
                .findFirst();
    }
    
    private static ArrayList<TextElement> getSummaryRows(PageRenderer firstPage){
        return firstPage.getElements().stream()
                .filter(TextElement.class::isInstance)
                .map(TextElement.class::cast)
                .filter(element -> element.getPageNumber() == 0)
                .filter(element -> isPNFMarkRow(element.getText()))
                .filter(element -> Math.abs(element.getRealX() - TABLE_X) < 1000)
                .filter(element -> element.getRealY() >= getRowY(0) - 1000)
                .sorted(Comparator.comparingInt(Element::getRealY))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    
    static String addMarkToRowText(String text, int rowIndex){
        int marks = countMarks(text);
        if(marks >= MAX_MARKS_PER_EXERCISE) return text;
        
        String label = getRowLabel(rowIndex);
        String marksText = getMarksText(text);
        return label + (marksText.isBlank() ? PNF_MARK : marksText + "  " + PNF_MARK);
    }
    
    static boolean isPNFMarkRow(String text){
        return text.matches("\\d+\\.\\s*(I\\s*)*");
    }
    
    static int countMarks(String text){
        return (int) text.chars().filter(c -> c == 'I').count();
    }
    
    static String getMarksText(String text){
        return text.replaceFirst("^\\d+\\.\\s*", "");
    }
    
    static String getRowLabel(int rowIndex){
        return (rowIndex + 1) + ". ";
    }
    
    // Names of the exercises of the grade scale (top-level grades).
    private static List<String> getExerciseNames(){
        if(GradeTreeView.getTotal() == null) return List.of();
        return GradeTreeView.getTotal().getChildren().stream()
                .map(item -> ((GradeTreeItem) item).getCore().getName())
                .toList();
    }
    
    // The PNF grade has no row in the table: the rows are the other exercises, in the order of the grade scale.
    public static boolean isPresentationExercise(String name){
        return Main.settings != null && Main.settings.presentationMarks.getValue() && isPNFExercise(name);
    }
    static boolean isPNFExercise(String name){
        return name != null && name.trim().equalsIgnoreCase(label());
    }
    static int getRowCount(List<String> exercises){
        return (int) exercises.stream().filter(name -> !isPNFExercise(name)).count();
    }
    // Row of the exercise at this index of the grade scale, or -1 (no exercise, or the PNF grade itself).
    static int getRowIndex(List<String> exercises, int exerciseIndex){
        if(exerciseIndex < 0 || exerciseIndex >= exercises.size() || isPNFExercise(exercises.get(exerciseIndex))) return -1;
        return (int) exercises.subList(0, exerciseIndex).stream().filter(name -> !isPNFExercise(name)).count();
    }
    
    static int getRowY(int rowIndex){
        return TABLE_HEADER_Y + TABLE_ROW_STEP * (rowIndex + 1);
    }
}
