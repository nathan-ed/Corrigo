/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.PNFAnnotationManager;
import corrigo.panel.sidebar.grades.QuickMarksBar;
import corrigo.panel.sidebar.grades.lines.LineBank.Use;
import corrigo.panel.sidebar.grades.lines.LineCorrection.Pending;
import corrigo.panel.sidebar.grades.lines.LineGrading.Block;
import corrigo.panel.sidebar.grades.lines.LineGrading.Line;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The grading panel of an exercise in the grading by lines: the lines already used in this exercise (a click adds
 * one), the text of the exercise (one block per sub-question, Tab completes a line, Ctrl+Enter opens the next copy),
 * what is not right (extra block, same line with other points elsewhere), the lines to place with a click on the
 * copy, and the marks (tick, cross, presentation mark with its comment).
 */
public class LinesEditor extends VBox {

    public record Colors(String card, String border, String text, String muted, String accent) {}

    private static final int USES_PER_SUB_QUESTION = 8;

    private final Colors colors;
    // Opens the next (1) or previous (-1) copy to grade
    private final IntConsumer openCopy;

    private final VBox bank = new VBox(3);
    private final Label bankTitle = new Label(TR.tr("lines.used"));
    private final TextArea area = new TextArea();
    private final Label suggestion = new Label();
    private final VBox warnings = new VBox(2);
    private final Label placing = new Label();
    private final Button placeAll = new Button(TR.tr("lines.placeAll"));
    private final HBox placingBox;
    private final ToggleButton pnf = new ToggleButton(TR.tr("lines.pnf"));
    private final TextField pnfComment = new TextField();

    private GradeTreeItem exercise;
    private List<Use> uses = List.of();
    private Use proposed;
    private boolean updating;
    // Placed by the last click on the copy, nothing typed since: Ctrl+Z in the text undoes this placement
    private boolean lastActionPlaced;

    public LinesEditor(Colors colors, QuickMarksBar quickMarks, IntConsumer openCopy){
        super(6);
        this.colors = colors;
        this.openCopy = openCopy;
        setPadding(new Insets(6, 0, 4, 0));

        bankTitle.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
        bankTitle.managedProperty().bind(bankTitle.visibleProperty());
        bank.managedProperty().bind(bank.visibleProperty());

        area.setWrapText(true);
        area.setPrefRowCount(9);
        area.setPromptText(TR.tr("lines.prompt"));
        area.setStyle("-fx-font-size: 14;");
        area.textProperty().addListener((o, oldValue, newValue) -> {
            if(updating || exercise == null) return;
            lastActionPlaced = false;
            LineCorrection.setText(exercise, newValue);
            updateSuggestion();
            updateWarnings();
        });
        area.caretPositionProperty().addListener((o, oldValue, newValue) -> updateSuggestion());
        area.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);

        suggestion.setStyle("-fx-font-size: 12; -fx-text-fill: " + colors.accent() + ";");
        suggestion.managedProperty().bind(suggestion.visibleProperty());
        warnings.managedProperty().bind(warnings.visibleProperty());

        placing.setWrapText(true);
        placing.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + colors.accent() + ";");
        placing.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(placing, Priority.ALWAYS);
        placeAll.setTooltip(new Tooltip(TR.tr("lines.placeAll.tooltip")));
        placeAll.setFocusTraversable(false);
        placeAll.setMinWidth(Region.USE_PREF_SIZE);
        placeAll.setOnAction(e -> {
            if(exercise != null) LineCorrection.placeAllNextToGrades(exercise);
            focusArea();
        });
        placingBox = new HBox(6, placing, placeAll);
        placingBox.setAlignment(Pos.CENTER_LEFT);
        placingBox.setPadding(new Insets(4, 6, 4, 6));
        placingBox.setStyle("-fx-background-color: rgba(21,101,192,.10); -fx-background-radius: 4;");
        placingBox.managedProperty().bind(placingBox.visibleProperty());

        // Marks: tick, cross, presentation mark (with its comment, typed here: accents and dead keys work)
        quickMarks.setOnlyTickAndCross(true);
        pnf.setTooltip(new Tooltip(TR.tr("lines.pnf.tooltip")));
        pnf.setFocusTraversable(false);
        pnf.setStyle("-fx-text-fill: #b31a1a; -fx-font-weight: bold;");
        pnf.selectedProperty().addListener((o, oldValue, newValue) -> refresh());
        pnfComment.setPromptText(TR.tr("lines.pnf.comment"));
        pnfComment.setPrefColumnCount(10);
        HBox.setHgrow(pnfComment, Priority.ALWAYS);
        pnfComment.setOnAction(e -> pnf.setSelected(true));
        pnf.setMinWidth(Region.USE_PREF_SIZE);
        quickMarks.setMinWidth(Region.USE_PREF_SIZE);
        pnfComment.setMinWidth(60);
        HBox marks = new HBox(6, quickMarks, pnf, pnfComment);
        marks.setAlignment(Pos.CENTER_LEFT);

        Label keys = new Label(TR.tr("lines.keys"));
        keys.setWrapText(true);
        keys.setStyle("-fx-font-size: 10; -fx-text-fill: " + colors.muted() + ";");

        getChildren().addAll(bankTitle, bank, area, suggestion, warnings, placingBox, marks, keys);
    }

    // EXERCISE

    // The panel shows this exercise (null: none)
    public void setExercise(GradeTreeItem exercise){
        boolean changed = this.exercise != exercise;
        this.exercise = exercise;
        if(changed) lastActionPlaced = false;
        if(exercise == null) return;
        if(changed){
            pnf.setSelected(false);
            uses = LineBank.collect(LineCorrection.getOtherTexts(exercise.getCore().getPath()), getLeafNames(),
                    PNFAnnotationManager.label(), MainWindow.gradesDigFormat);
            updateBank();
        }
        String text = LineCorrection.getText(exercise.getCore().getPath());
        if(!area.getText().equals(text)){
            updating = true;
            try{
                area.setText(text);
                area.positionCaret(text.length());
            }finally{
                updating = false;
            }
        }
        refresh();
    }
    public GradeTreeItem getExercise(){
        return exercise;
    }

    public void focusArea(){
        Platform.runLater(() -> {
            area.requestFocus();
            if(area.getSelection().getLength() == 0 && area.getCaretPosition() == 0) area.positionCaret(area.getText().length());
        });
    }

    // The lines to place, the warnings
    public void refresh(){
        if(exercise == null) return;
        updateWarnings();
        updateSuggestion();
        List<Pending> unplaced = LineCorrection.getUnplaced(exercise);
        if(pnf.isSelected()){
            placing.setText(TR.tr("lines.pnf.hint", PNFAnnotationManager.getText(pnfComment.getText())));
            placingBox.setVisible(true);
            placeAll.setVisible(false);
        }else if(!unplaced.isEmpty()){
            String next = LineCorrection.getCopyText(unplaced.getFirst().line()).replace(LineGrading.CHECK, "✓").replace(LineGrading.CROSS, "✗");
            placing.setText(TR.tr("lines.toPlace", next, String.valueOf(unplaced.size())));
            placingBox.setVisible(true);
            placeAll.setVisible(true);
        }else placingBox.setVisible(false);
    }

    private List<String> getLeafNames(){
        return LineCorrection.getLeaves(exercise).stream().map(leaf -> leaf.getCore().getName()).toList();
    }

    // LINES ALREADY USED

    private void updateBank(){
        bank.getChildren().clear();
        List<GradeTreeItem> leaves = LineCorrection.getLeaves(exercise);
        for(int leaf = 0; leaf < leaves.size(); leaf++){
            int index = leaf;
            List<Use> leafUses = uses.stream().filter(use -> use.leaf() == index).limit(USES_PER_SUB_QUESTION).toList();
            if(leafUses.isEmpty()) continue;
            FlowPane row = new FlowPane(4, 4); // One row per sub-question
            if(leaves.size() > 1){
                Label name = new Label(LineGrading.labelOf(leaves.get(leaf).getCore().getName()));
                name.setStyle("-fx-font-weight: bold; -fx-font-size: 11; -fx-text-fill: " + colors.text() + ";");
                row.getChildren().add(name);
            }
            for(Use use : leafUses){
                // The number of copies is after "×": not to be read as points
                Label chip = new Label(use.text().replace(LineGrading.CHECK, "✓").replace(LineGrading.CROSS, "✗") + "  ×" + use.copies());
                chip.setStyle("-fx-font-size: 11; -fx-padding: 1 6; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: "
                        + colors.border() + "; -fx-background-color: " + colors.card() + "; -fx-text-fill: " + colors.text() + ";");
                chip.setCursor(Cursor.HAND);
                chip.setTooltip(new Tooltip(use.text()));
                chip.setOnMouseClicked(e -> insertLine(use.text(), use.leaf()));
                row.getChildren().add(chip);
            }
            bank.getChildren().add(row);
        }
        boolean shown = !bank.getChildren().isEmpty();
        bank.setVisible(shown);
        bankTitle.setVisible(shown);
    }

    // Writes a line of this sub-question: where the caret is if it is in its block, else at the end of its block, else
    // in a new block at the end (with "b)" before it when it does not follow the last block)
    void insertLine(String text, int leaf){
        String current = area.getText();
        if(getCaretLeaf() == leaf){
            int caret = Math.clamp(area.getCaretPosition(), 0, current.length());
            int start = current.lastIndexOf('\n', caret - 1) + 1;
            int end = current.indexOf('\n', caret);
            if(end < 0) end = current.length();
            if(current.substring(start, end).isBlank()){
                area.replaceText(start, end, text);
                area.positionCaret(start + text.length());
            }else{
                area.insertText(end, "\n" + text);
                area.positionCaret(end + 1 + text.length());
            }
        }else{
            List<Block> blocks = LineGrading.parse(current, getLeafNames(), PNFAnnotationManager.label());
            Block block = blocks.stream().filter(b -> b.leaf() == leaf).reduce((first, second) -> second).orElse(null);
            if(block != null){ // After the last line of its block
                int lineIndex = block.lines().getLast().index();
                int end = lineEnd(current, lineIndex);
                area.insertText(end, "\n" + text);
                area.positionCaret(end + 1 + text.length());
            }else{
                int lastLeaf = blocks.isEmpty() ? -1 : blocks.getLast().leaf();
                String label = leaf == lastLeaf + 1 ? "" : LineGrading.labelOf(getLeafNames().get(leaf)) + "\n";
                String before = current.isBlank() ? "" : current.stripTrailing() + "\n\n";
                if(current.isBlank() && leaf == 0) label = "";
                area.replaceText(0, current.length(), before + label + text); // Undoable, unlike setText
                area.positionCaret(area.getText().length());
            }
        }
        area.requestFocus();
    }
    // Position of the end of this line of the text
    private static int lineEnd(String text, int lineIndex){
        int position = 0;
        for(int i = 0; i < lineIndex; i++){
            int next = text.indexOf('\n', position);
            if(next < 0) return text.length();
            position = next + 1;
        }
        int end = text.indexOf('\n', position);
        return end < 0 ? text.length() : end;
    }

    // COMPLETION

    private int getCaretLine(){
        String text = area.getText();
        int caret = Math.clamp(area.getCaretPosition(), 0, text.length());
        return (int) text.substring(0, caret).chars().filter(c -> c == '\n').count();
    }
    // Sub-question of the line of the caret: its block, or the block that the next line starts
    private int getCaretLeaf(){
        int caretLine = getCaretLine();
        List<Block> blocks = LineGrading.parse(area.getText(), getLeafNames(), PNFAnnotationManager.label());
        int leaf = 0;
        for(Block block : blocks){
            if(block.lines().stream().anyMatch(line -> line.index() == caretLine)) return block.leaf();
            if(block.lines().getLast().index() < caretLine) leaf = block.leaf() + 1;
        }
        return leaf;
    }
    private String getCaretLineText(){
        String text = area.getText();
        int caret = Math.clamp(area.getCaretPosition(), 0, text.length());
        int start = text.lastIndexOf('\n', caret - 1) + 1;
        int end = text.indexOf('\n', caret);
        return text.substring(start, end < 0 ? text.length() : end);
    }
    private void updateSuggestion(){
        proposed = null;
        if(exercise != null && area.isFocused()){
            String typed = getCaretLineText();
            Line line = LineGrading.parseLine(0, typed, PNFAnnotationManager.label());
            if(line.points() == null) proposed = LineBank.complete(uses, typed, getCaretLeaf());
        }
        suggestion.setText(proposed == null ? "" : TR.tr("lines.suggestion", proposed.text()));
        suggestion.setVisible(proposed != null);
    }

    // WARNINGS

    private void updateWarnings(){
        warnings.getChildren().clear();
        if(exercise != null){
            List<Block> blocks = LineCorrection.getBlocks(exercise);
            int leaves = LineCorrection.getLeaves(exercise).size();
            for(Block block : blocks){
                if(block.leaf() < 0) addWarning(TR.tr("lines.block.extra", String.valueOf(leaves)) + " : « " + block.lines().getFirst().raw().strip() + " »");
                for(Line line : block.lines()){
                    Use conflict = LineBank.findConflict(uses, line, block.leaf());
                    if(conflict != null) addWarning(TR.tr("lines.conflict", line.shown().isEmpty() ? line.raw().strip() : line.shown(),
                            LineGrading.formatPoints(conflict.points(), MainWindow.gradesDigFormat), String.valueOf(conflict.copies())));
                }
            }
        }
        warnings.setVisible(!warnings.getChildren().isEmpty());
    }
    private void addWarning(String text){
        Label label = new Label("⚠ " + text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 12; -fx-text-fill: #e65100;");
        warnings.getChildren().add(label);
    }

    // KEYS

    private void onKey(KeyEvent e){
        // Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y in the text: its own undo, not the undo of the copy. Its history starts with the
        // copy (setExercise sets the text, which resets it): never the text of another copy.
        if(e.isShortcutDown() && (e.getCode() == KeyCode.Z || e.getCode() == KeyCode.Y)){
            e.consume();
            boolean redo = e.getCode() == KeyCode.Y || e.isShiftDown();
            if(!redo && lastActionPlaced){ // A click just placed a line or a mark: it goes back to the lines to place
                lastActionPlaced = false;
                MainWindow.mainScreen.undo();
                refresh();
                return;
            }
            if(redo && area.isRedoable()) area.redo();
            else if(!redo && area.isUndoable()) area.undo();
            return;
        }
        if(e.getCode() == KeyCode.ENTER && e.isShortcutDown()){
            e.consume();
            openCopy.accept(e.isShiftDown() ? -1 : 1);
        }else if(e.getCode() == KeyCode.TAB && !e.isShortcutDown()){
            e.consume(); // Never a tab character in the text
            if(proposed != null && !e.isShiftDown()){
                String text = area.getText();
                int caret = Math.clamp(area.getCaretPosition(), 0, text.length());
                int start = text.lastIndexOf('\n', caret - 1) + 1;
                int end = text.indexOf('\n', caret);
                if(end < 0) end = text.length();
                area.replaceText(start, end, proposed.text());
                area.positionCaret(start + proposed.text().length());
            }
        }else if(e.getCode() == KeyCode.ESCAPE && pnf.isSelected()){
            e.consume();
            pnf.setSelected(false);
        }
    }

    // PLACEMENT

    // Something waits for a click on the copy (a presentation mark, or lines to place)
    public boolean isWaitingForClick(){
        return exercise != null && (pnf.isSelected() || !LineCorrection.getUnplaced(exercise).isEmpty());
    }
    // A click on a page: places the presentation mark, or the next line to place. Returns true if something was placed.
    public boolean onPageClicked(PageRenderer page, int x, int y){
        if(exercise == null || exercise.isDeleted()) return false;
        boolean placed = false;
        if(pnf.isSelected()){
            String comment = pnfComment.getText().strip();
            int index = fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView.getTotal().getChildren().indexOf(exercise);
            TextElement mark = PNFAnnotationManager.addPNFAtGrid(page, x, y, comment.isEmpty() ? null : comment, index);
            placed = mark != null;
            pnf.setSelected(false);
            pnfComment.clear();
        }else{
            List<Pending> unplaced = LineCorrection.getUnplaced(exercise);
            if(!unplaced.isEmpty()) placed = LineCorrection.place(exercise, unplaced.getFirst(), page, x, y, true) != null;
        }
        lastActionPlaced = placed;
        refresh();
        focusArea();
        return placed;
    }
}
