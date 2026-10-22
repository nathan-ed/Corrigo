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
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The grading panel of an exercise in the grading by lines: the text of the exercise (one block per sub-question,
 * Ctrl+Enter opens the next copy), under it the lines already used that match the line being typed (↑ ↓ to choose,
 * Tab or a click to take one), what is not right (extra block, same line with other points elsewhere), the lines to
 * place with a click on the copy, and the marks (tick, cross, presentation mark with its comment).
 */
public final class LinesEditor extends VBox {

    public record Colors(String card, String border, String text, String muted, String accent) {}
    // Where the keys of the text go (delta: -1 previous, 1 next), the text ready to type once there
    public interface Navigation {
        void ungradedCopy(int delta); // Ctrl+Enter, Ctrl+Shift+Enter: the next copy where the exercise is not graded
        void copy(int delta); // Alt+PageDown, Alt+PageUp: the next copy of the list, same exercise
        void exercise(int delta); // Alt+↓, Alt+↑
    }

    private static final int CHOICES = 6;

    private final Colors colors;
    private final Navigation navigation;

    private final TextArea area = new TextArea();
    private final VBox choicesBox = new VBox(1);
    private final VBox warnings = new VBox(2);
    private final Label placing = new Label();
    private final Button placeAll = new Button(TR.tr("lines.placeAll"));
    private final HBox placingBox;
    private final ToggleButton pnf = new ToggleButton(TR.tr("lines.pnf"));
    private final TextField pnfComment = new TextField();

    private GradeTreeItem exercise;
    private List<Use> uses = List.of(); // Lines of this exercise on the other copies
    private List<Use> everywhere = List.of(); // Typed with "g", and the presentation marks: in every exercise
    private List<Use> choices = List.of(); // Shown under the text for the line being typed
    private int chosen; // Index in choices
    private String dismissed; // The line for which Escape hid the choices
    private int newLineAt = -1; // A letter typed with the caret here starts a new line (-1: none)
    private LineCorrection.TextEdit edit; // The edit of the text being applied
    private boolean updating;
    // Placed by the last click on the copy, nothing typed since: Ctrl+Z in the text undoes this placement
    private boolean lastActionPlaced;

    public LinesEditor(Colors colors, QuickMarksBar quickMarks, Navigation navigation){
        super(6);
        this.colors = colors;
        this.navigation = navigation;
        setPadding(new Insets(6, 0, 4, 0));

        area.setWrapText(true);
        area.setPrefRowCount(9);
        area.setPromptText(TR.tr("lines.prompt"));
        area.setStyle("-fx-font-size: 14;");
        // The part of the text each edit replaces: the lines out of it keep their ids (two equal lines, one deleted:
        // the text of the other one stays on the copy)
        area.setTextFormatter(new TextFormatter<>(change -> {
            edit = new LineCorrection.TextEdit(change.getRangeStart(), change.getRangeEnd(), change.getText());
            return change;
        }));
        area.textProperty().addListener((o, oldValue, newValue) -> {
            LineCorrection.TextEdit applied = edit;
            edit = null;
            if(updating || exercise == null) return;
            lastActionPlaced = false;
            LineCorrection.setText(exercise, newValue, applied);
            if(!getCaretLineText().equals(dismissed)) dismissed = null; // Escape hides the choices of one line only
            updateChoices();
            updateWarnings();
        });
        area.caretPositionProperty().addListener((o, oldValue, newValue) -> updateChoices());
        area.focusedProperty().addListener((o, oldValue, newValue) -> updateChoices());
        area.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        area.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            String typed = e.getCharacter();
            if(typed.isEmpty() || Character.isISOControl(typed.charAt(0))) return; // Tab, Return: their key did it
            // A line was just added, or the field got the focus back: what is typed starts a new line
            if(newLineAt >= 0 && newLineAt == area.getCaretPosition() && area.getSelection().getLength() == 0
                    && !e.isShortcutDown()) area.insertText(newLineAt, "\n");
            newLineAt = -1;
        });
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> newLineAt = -1); // A click in the text: the teacher chose where
        // A click under the last line (the empty part of the field): what is typed starts a new line, as when the field
        // gets the focus
        area.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if(area.getSelection().getLength() == 0 && e.getSceneY() > getTextBottom()) focusAreaNow();
        });

        choicesBox.setPadding(new Insets(2));
        choicesBox.setStyle("-fx-background-color: " + colors.card() + "; -fx-border-color: " + colors.border() + "; -fx-border-radius: 3;");
        choicesBox.managedProperty().bind(choicesBox.visibleProperty());
        choicesBox.setVisible(false);
        warnings.managedProperty().bind(warnings.visibleProperty());

        placing.setWrapText(true);
        placing.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + colors.accent() + ";");
        placing.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(placing, Priority.ALWAYS);
        placeAll.setTooltip(new Tooltip(TR.tr("lines.placeAll.tooltip")));
        placeAll.setFocusTraversable(false);
        placeAll.setMinWidth(Region.USE_PREF_SIZE);
        placeAll.setOnAction(e -> placeAllNextToGrades());
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

        getChildren().addAll(area, choicesBox, warnings, placingBox, marks, keys);
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
            everywhere = collectEverywhere();
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
        Platform.runLater(this::focusAreaNow);
    }
    // The bottom of the text written in the field, in the scene (its last line)
    private double getTextBottom(){
        double bottom = Double.NEGATIVE_INFINITY;
        for(Node node : area.lookupAll(".text")){
            if(node instanceof Text text && text.isVisible() && !text.getText().isEmpty()){
                bottom = Math.max(bottom, text.localToScene(text.getBoundsInLocal()).getMaxY());
            }
        }
        return bottom;
    }
    // The focus in the text, at its end: what is typed next starts a new line, never stuck to the last line
    public void focusAreaNow(){
        area.requestFocus();
        String text = area.getText();
        area.positionCaret(text.length());
        newLineAt = text.isEmpty() || text.endsWith("\n") ? -1 : text.length();
    }

    // The lines to place, the warnings
    public void refresh(){
        if(exercise == null) return;
        updateWarnings();
        updateChoices();
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

    // The lines typed with "g" and the presentation marks, in every exercise of every copy (this one included)
    private static List<Use> collectEverywhere(){
        HashMap<String, List<String>> leafNames = new HashMap<>();
        for(var child : GradeTreeView.getTotal().getChildren()){
            if(child instanceof GradeTreeItem item) leafNames.put(item.getCore().getPath(),
                    LineCorrection.getLeaves(item).stream().map(leaf -> leaf.getCore().getName()).toList());
        }
        ArrayList<List<Block>> copies = new ArrayList<>();
        for(Map<String, String> texts : LineCorrection.getAllCopiesTexts()){
            ArrayList<Block> blocks = new ArrayList<>();
            texts.forEach((path, text) -> blocks.addAll(LineGrading.parse(text, leafNames.getOrDefault(path, List.of(path)), PNFAnnotationManager.label())));
            copies.add(blocks);
        }
        return LineBank.collectEverywhere(copies, MainWindow.gradesDigFormat);
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
            int last = block.lines().getLast().index();
            // Its line, or the empty line just after it: what is typed there goes to this block
            if(block.lines().stream().anyMatch(line -> line.index() == caretLine) || last + 1 == caretLine) return block.leaf();
            if(last < caretLine) leaf = block.leaf() + 1;
        }
        return leaf;
    }
    // Start and end of the line of the caret in the text
    private int[] getCaretLineBounds(){
        String text = area.getText();
        int caret = Math.clamp(area.getCaretPosition(), 0, text.length());
        int end = text.indexOf('\n', caret);
        return new int[]{text.lastIndexOf('\n', caret - 1) + 1, end < 0 ? text.length() : end};
    }
    private String getCaretLineText(){
        int[] bounds = getCaretLineBounds();
        return area.getText().substring(bounds[0], bounds[1]);
    }
    // The lines already used that match the line being typed (the caret at its end, its points not typed yet), the
    // best first
    private void updateChoices(){
        List<Use> found = List.of();
        String typed = getCaretLineText();
        if(exercise != null && area.isFocused() && !typed.equals(dismissed) && area.getCaretPosition() == getCaretLineBounds()[1]
                && LineGrading.parseLine(0, typed, PNFAnnotationManager.label()).points() == null){
            ArrayList<Use> all = new ArrayList<>(uses);
            all.addAll(everywhere);
            found = LineBank.matches(all, typed, getCaretLeaf(), CHOICES);
        }
        if(!found.equals(choices)) chosen = 0;
        choices = found;
        choicesBox.getChildren().clear();
        for(int i = 0; i < choices.size(); i++){
            Use use = choices.get(i);
            // The number of copies is after "×": not to be read as points
            Label choice = new Label(use.text().replace(LineGrading.CHECK, "✓").replace(LineGrading.CROSS, "✗") + "   ×" + use.copies());
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.setPadding(new Insets(1, 6, 1, 6));
            choice.setStyle("-fx-font-size: 12; -fx-text-fill: " + colors.text() + ";"
                    + (i == chosen ? " -fx-background-color: rgba(21,101,192,.18); -fx-background-radius: 2;" : ""));
            choice.setCursor(Cursor.HAND);
            int index = i;
            choice.setOnMousePressed(e -> { // Before the text loses the focus
                e.consume();
                take(choices.get(index));
            });
            choicesBox.getChildren().add(choice);
        }
        choicesBox.setVisible(!choices.isEmpty());
    }
    // The line of the caret becomes this line; what is typed next goes on a new line
    private void take(Use use){
        int[] bounds = getCaretLineBounds();
        area.replaceText(bounds[0], bounds[1], use.text());
        area.positionCaret(bounds[0] + use.text().length());
        area.requestFocus();
        newLineAt = area.getCaretPosition();
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
                    if(LineGrading.hasPointsFirst(line)) addWarning(TR.tr("lines.pointsFirst", line.raw().strip()));
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
        // A key that moves or deletes (not a letter, nor Shift for a capital, nor a shortcut as Ctrl+S): what is typed
        // next stays where the caret is
        boolean shortcut = e.isShortcutDown() || e.isControlDown() || e.isAltDown();
        if(!shortcut && !e.getCode().isModifierKey() && (e.getText().isEmpty() || Character.isISOControl(e.getText().charAt(0)))) newLineAt = -1;
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
            newLineAt = -1; // The text changes: what is typed next goes where the caret is
            if(redo && area.isRedoable()) area.redo();
            else if(!redo && area.isUndoable()){
                area.undo();
                LineCorrection.restoreRemoved(exercise); // Lines deleted by mistake: back on the copy where they were
            }
            return;
        }
        if(e.getCode() == KeyCode.ENTER && e.isShortcutDown()){
            e.consume();
            navigation.ungradedCopy(e.isShiftDown() ? -1 : 1);
        }else if(e.getCode() == KeyCode.ENTER && e.isAltDown()){
            e.consume();
            placeAllNextToGrades();
        }else if((e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) && e.isAltDown()){
            e.consume(); // The previous or next exercise (the text field would keep the key)
            navigation.exercise(e.getCode() == KeyCode.UP ? -1 : 1);
        }else if((e.getCode() == KeyCode.PAGE_UP || e.getCode() == KeyCode.PAGE_DOWN) && e.isAltDown()){
            e.consume(); // The previous or next copy of the list, same exercise
            navigation.copy(e.getCode() == KeyCode.PAGE_UP ? -1 : 1);
        }else if(e.getCode() == KeyCode.TAB && !e.isShortcutDown()){
            e.consume(); // Never a tab character in the text
            if(!choices.isEmpty() && !e.isShiftDown()) take(choices.get(chosen));
        }else if((e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) && !choices.isEmpty() && !e.isShortcutDown()){
            e.consume(); // Chooses among the lines shown, the caret stays
            chosen = Math.floorMod(chosen + (e.getCode() == KeyCode.DOWN ? 1 : -1), choices.size());
            updateChoices();
        }else if(e.getCode() == KeyCode.ESCAPE && !choices.isEmpty()){
            e.consume();
            dismissed = getCaretLineText();
            updateChoices();
        }else if(e.getCode() == KeyCode.ESCAPE && pnf.isSelected()){
            e.consume();
            pnf.setSelected(false);
        }
    }

    // PLACEMENT

    // The lines not placed yet, next to the grades of their sub-questions (the button, Alt+Enter)
    private void placeAllNextToGrades(){
        if(exercise != null) LineCorrection.placeAllNextToGrades(exercise);
        focusArea();
    }

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
