/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.datasaving.simpleconfigs.ExerciseCorrectionData;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredComment;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentCatalog;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredComments;
import fr.clementgre.pdf4teachers.utils.MathUtils;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import fr.clementgre.pdf4teachers.document.editions.elements.ScoredCommentElement;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Grading panel docked at the right of the document, inspired by Gradescope:
 * all the sub-grades of the selected exercise with their scored comments (rubric items),
 * points and comments, a general comment, and "Next ungraded" to open the next copy to grade.
 */
public class GradingPanel extends VBox {


    private record Palette(String background, String card, String border, String text, String muted, String accent, String activeBackground){
        static Palette get(){
            if(StyleManager.DEFAULT_STYLE == jfxtras.styles.jmetro.Style.DARK)
                return new Palette("#1e1e1e", "#2a2a2a", "#3c3c3c", "#e8e8e8", "#9e9e9e", "#4a9eff", "#232e3b");
            return new Palette("#f4f4f4", "#ffffff", "#d9d9d9", "#1c1c1c", "#6b6b6b", "#1565c0", "#e7f0fc");
        }
    }
    private final Palette palette = Palette.get();

    // HEADER
    private final Label exerciseName = new Label();
    private final Label exerciseScore = new Label();
    private final Label copyInfo = new Label();
    private final Button previousExercise = new Button("‹");
    private final Button nextExercise = new Button("›");

    // CONTENT
    private final VBox sectionsBox = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(sectionsBox);
    private final VBox generalBox = new VBox(6);
    private final TextField general = new TextField();

    // FOOTER
    private final Button previousUngraded = new Button();
    private final Button nextUngraded = new Button();
    private final Label hint = new Label();

    private GradeTreeItem exercise;
    private final ArrayList<Section> sections = new ArrayList<>();
    private int active; // Index of the active section, or sections.size() for the general comment
    private final ArrayList<Runnable> unbinders = new ArrayList<>();

    // Text waiting for a click on a page to be placed there
    private String pendingText;
    private String pendingPath;
    private Runnable afterPendingPlaced;

    private boolean focusOnNextReload;
    private boolean swallowNextTyped;
    private final PauseTransition followPageDelay = new PauseTransition(Duration.millis(250));

    public GradingPanel(){
        setStyle("-fx-background-color: " + palette.background() + ";");

        setupHeader();
        setupContent();
        setupFooter();


        MainWindow.mainScreen.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMainScreenPressed);
        MainWindow.mainScreen.isEditPagesModeProperty().addListener((o, oldValue, newValue) -> reload());
        MainWindow.mainScreen.statusProperty().addListener((o, oldValue, newValue) -> reload());
        // The panel follows the page being read
        followPageDelay.setOnFinished(e -> followVisiblePage());
        MainWindow.mainScreen.pane.translateYProperty().addListener((o, oldValue, newValue) -> followPageDelay.playFromStart());
        // The panel follows the grade selected in the grade tree or on the document (a grade being entered)
        MainWindow.gradeTab.treeView.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) -> {
            // Later: the selection also changes while a document is loaded or closed
            if(newValue instanceof GradeTreeItem item) Platform.runLater(() -> {
                if(!item.isDeleted() && MainWindow.gradeTab.treeView.getSelectionModel().getSelectedItem() == item) onGradeSelected(item);
            });
        });
        ScoredComments.revisionProperty().addListener((o, oldValue, newValue) -> scheduleUpdate(false));
    }

    // SETUP

    private void setupHeader(){
        exerciseName.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: " + palette.text() + ";");
        exerciseName.setMaxWidth(Double.MAX_VALUE);
        exerciseName.setMinWidth(0);
        HBox.setHgrow(exerciseName, Priority.ALWAYS);
        exerciseScore.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: " + palette.accent() + ";");
        exerciseScore.setMinWidth(Region.USE_PREF_SIZE);
        copyInfo.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.muted() + ";");

        for(Button button : new Button[]{previousExercise, nextExercise}){
            button.setFocusTraversable(false);
            button.setStyle("-fx-font-size: 16; -fx-padding: 0 8; -fx-background-color: transparent; -fx-text-fill: " + palette.text() + ";");
            button.setCursor(Cursor.HAND);
        }
        previousExercise.setTooltip(new Tooltip(TR.tr("gradingPanel.previousExercise")));
        nextExercise.setTooltip(new Tooltip(TR.tr("gradingPanel.nextExercise")));
        previousExercise.setOnAction(e -> MainWindow.footerBar.selectExercise(-1));
        nextExercise.setOnAction(e -> MainWindow.footerBar.selectExercise(1));

        HBox title = new HBox(2, previousExercise, exerciseName, nextExercise, exerciseScore);
        title.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(2, title, copyInfo);
        header.setPadding(new Insets(10, 12, 10, 8));
        header.setStyle("-fx-border-color: " + palette.border() + "; -fx-border-width: 0 0 1 0;");
        getChildren().add(header);
    }

    private void setupContent(){
        sectionsBox.setPadding(new Insets(10, 10, 10, 10));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFocusTraversable(true);
        scroll.setStyle("-fx-background: " + palette.background() + "; -fx-background-color: " + palette.background() + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        scroll.addEventFilter(KeyEvent.KEY_PRESSED, this::onPanelKey);
        // A letter shortcut that focuses a field (C, N, G) must not be typed in it
        scroll.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if(swallowNextTyped){
                swallowNextTyped = false;
                e.consume();
            }
        });
        scroll.addEventFilter(KeyEvent.KEY_RELEASED, e -> swallowNextTyped = false);
        // A click in the empty space gives the keyboard to the panel
        sectionsBox.setOnMousePressed(e -> scroll.requestFocus());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        Label generalTitle = new Label(TR.tr("gradingPanel.generalTitle"));
        generalTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: " + palette.text() + ";");
        general.setOnMousePressed(e -> setActive(sections.size(), false));
        setupLiveComment(general, () -> exercise);
        general.focusedProperty().addListener((o, oldValue, newValue) -> {
            if(!newValue) commitComment(general, exercise, false, null);
        });
        general.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch(e.getCode()){
                case ENTER -> {
                    e.consume();
                    int delta = e.isShiftDown() ? -1 : 1;
                    if(e.isShortcutDown()) commitComment(general, exercise, true, null);
                    else commitComment(general, exercise, false, () -> openUngradedCopy(delta));
                }
                case ESCAPE, TAB -> {
                    e.consume();
                    if(pendingText != null) cancelPending();
                    if(e.getCode() == KeyCode.ESCAPE) general.setText(getCommentText(exercise)); // Cancels the edit
                    else commitComment(general, exercise, false, null);
                    scroll.requestFocus();
                }
                case UP -> {
                    e.consume();
                    setActive(sections.size() - 1, true);
                }
            }
        });
        generalBox.getChildren().addAll(generalTitle, general);
    }

    private void setupFooter(){
        previousUngraded.setText(TR.tr("gradingPanel.previousUngraded"));
        nextUngraded.setText(TR.tr("gradingPanel.nextUngraded"));
        previousUngraded.setTooltip(new Tooltip("Shift+Z"));
        nextUngraded.setTooltip(new Tooltip("Z"));
        previousUngraded.setFocusTraversable(false);
        nextUngraded.setFocusTraversable(false);
        previousUngraded.setOnAction(e -> commitComment(general, exercise, false, () -> openUngradedCopy(-1)));
        nextUngraded.setOnAction(e -> commitComment(general, exercise, false, () -> openUngradedCopy(1)));
        nextUngraded.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nextUngraded, Priority.ALWAYS);
        nextUngraded.setStyle("-fx-background-color: " + palette.accent() + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 12; -fx-background-radius: 4;");
        previousUngraded.setStyle("-fx-padding: 7 12; -fx-background-radius: 4;");

        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.muted() + ";");

        HBox buttons = new HBox(8, previousUngraded, nextUngraded);
        VBox footer = new VBox(8, buttons, hint);
        footer.setPadding(new Insets(10, 12, 10, 12));
        footer.setStyle("-fx-border-color: " + palette.border() + "; -fx-border-width: 1 0 0 0;");
        getChildren().add(footer);
    }

    // STATE

    /**
     * Rebuilds the panel for the selected exercise (exercise changed, file opened, grade scale edited).
     * The active sub-grade is kept if the exercise did not change.
     */
    public void reload(){
        scheduleUpdate(true);
    }
    
    /**
     * The automatic updates are done later, once the current action is finished: while a document is loaded or closed,
     * its grade scale is incomplete, and reading the scored comments catalog would make it follow this partial grade scale.
     */
    private boolean updateScheduled;
    private boolean fullUpdateScheduled;
    private void scheduleUpdate(boolean full){
        fullUpdateScheduled |= full;
        if(updateScheduled) return;
        updateScheduled = true;
        Platform.runLater(() -> {
            updateScheduled = false;
            boolean reload = fullUpdateScheduled;
            fullUpdateScheduled = false;
            if(reload) reloadNow();
            else refresh();
        });
    }
    
    private void reloadNow(){
        commitEdits();
        GradeTreeItem newExercise = getSelectedExercise();
        if(newExercise == null || exercise == null || newExercise.getCore() != exercise.getCore()) active = 0;
        exercise = newExercise;
        cancelPending();

        unbinders.forEach(Runnable::run);
        unbinders.clear();
        sections.clear();
        sectionsBox.getChildren().clear();

        if(exercise == null){
            exerciseName.setText("");
            exerciseScore.setText("");
            copyInfo.setText("");
            previousExercise.setDisable(true);
            nextExercise.setDisable(true);
            Label empty = new Label(TR.tr("gradingPanel.noGradeScale"));
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: " + palette.muted() + ";");
            sectionsBox.getChildren().add(empty);
            return;
        }

        List<GradeTreeItem> leaves = exercise.hasSubGrade()
                ? GradeTreeView.getGradesArray(exercise).stream().filter(item -> !item.hasSubGrade()).toList()
                : List.of(exercise);
        for(GradeTreeItem leaf : leaves){
            Section section = new Section(leaf);
            sections.add(section);
            sectionsBox.getChildren().add(section);
        }
        sectionsBox.getChildren().add(generalBox);
        active = Math.clamp(active, 0, sections.size());

        bindValue(exercise.getCore(), this::updateHeader);
        refresh();

        if(focusOnNextReload){
            focusOnNextReload = false;
            Platform.runLater(this::focusPanel);
        }
    }

    // The comments may have been edited in the texts tab
    public void onTabSelected(){
        if(exercise != null) refresh();
    }
    
    private static GradeTreeItem getSelectedExercise(){
        if(MainWindow.footerBar == null || MainWindow.gradeTab == null) return null;
        if(!MainWindow.mainScreen.hasDocument(false) || MainWindow.mainScreen.isEditPagesMode()) return null;
        if(GradeTreeView.getTotal() == null) return null;

        if(GradeTreeView.getTotal().isDeleted()) return null; // The document is being closed
        int index = MainWindow.footerBar.getSelectedExerciseIndex();
        if(index < 0 || index >= GradeTreeView.getTotal().getChildren().size()) return null;
        GradeTreeItem exercise = (GradeTreeItem) GradeTreeView.getTotal().getChildren().get(index);
        return exercise.isDeleted() ? null : exercise;
    }

    private void bindValue(GradeElement grade, Runnable onChange){
        ChangeListener<Number> listener = (o, oldValue, newValue) -> onChange.run();
        grade.valueProperty().addListener(listener);
        grade.totalProperty().addListener(listener);
        unbinders.add(() -> {
            grade.valueProperty().removeListener(listener);
            grade.totalProperty().removeListener(listener);
        });
    }

    private void refresh(){
        if(exercise == null) return;
        GradeTreeItem selected = getSelectedExercise();
        if(exercise.isDeleted() || selected == null || selected.getCore() != exercise.getCore()
                || sections.stream().anyMatch(section -> section.leaf.isDeleted())){
            // The grade scale changed (or the document is being closed): rebuild later, never recursively.
            scheduleUpdate(true);
            return;
        }
        updateHeader();
        sections.forEach(Section::update);
        if(!general.isFocused()) general.setText(getCommentText(exercise));
        updateActiveStyle();
        hint.setText(TR.tr(pendingText != null ? "gradingPanel.hint.clickToPlace" : "gradingPanel.hint"));
    }

    private void updateHeader(){
        if(exercise == null) return;
        exerciseName.setText(exercise.getCore().getName());
        exerciseScore.setText(formatScore(exercise.getCore()));
        int exercises = MainWindow.footerBar.getExerciseCount();
        int index = MainWindow.footerBar.getSelectedExerciseIndex();
        previousExercise.setDisable(index <= 0);
        nextExercise.setDisable(index >= exercises - 1);

        int files = MainWindow.filesTab.files.getItems().size();
        int file = MainWindow.filesTab.files.getSelectionModel().getSelectedIndex();
        copyInfo.setText(TR.tr("gradingPanel.copy", MainWindow.mainScreen.document.getFileName(), String.valueOf(file + 1), String.valueOf(files)));
    }

    private static String formatScore(GradeElement grade){
        String value = grade.getValue() == -1 ? "–" : MainWindow.gradesDigFormat.format(grade.getValue());
        return value + " / " + MainWindow.gradesDigFormat.format(grade.getTotal());
    }

    // ACTIVE SECTION

    private Section getActive(){
        return active < sections.size() ? sections.get(active) : null;
    }

    private void setActive(int index, boolean focus){
        if(exercise == null) return;
        Section old = getActive();
        if(old != null) old.applyPoints();
        cancelPending();
        active = Math.clamp(index, 0, sections.size());
        updateActiveStyle();

        Node target = getActive() == null ? generalBox : getActive();
        Platform.runLater(() -> ensureVisible(target));
        if(focus){
            selectInTree();
            // After the tree item selection, which focuses its own field
            Platform.runLater(this::focusPanel);
        }
    }

    private void updateActiveStyle(){
        for(int i = 0; i < sections.size(); i++) sections.get(i).markActive(i == active);
        general.setStyle(active == sections.size() ? "-fx-border-color: " + palette.accent() + "; -fx-border-width: 2;" : "");
    }

    // The grade tree and the scored comments panel follow the panel.
    private boolean selectingInTree;
    private void selectInTree(){
        GradeTreeItem item = getActive() == null ? exercise : getActive().leaf;
        if(item == null) return;
        selectingInTree = true;
        try{
            MainWindow.gradeTab.treeView.getSelectionModel().select(item);
        }finally{
            selectingInTree = false;
        }
    }
    
    // Shows the exercise whose sub-grades are on the page at the middle of the screen, and activates its first sub-grade of this page.
    private void followVisiblePage(){
        if(!MainWindow.mainScreen.hasDocument(false) || GradeTreeView.getTotal() == null || MainWindow.mainScreen.isEditPagesMode()) return;
        PageRenderer visible = MainWindow.mainScreen.document.getCenterVisiblePage();
        if(visible == null) return;
        int page = visible.getPage();
        
        // Already on a sub-grade of this page
        Section current = getActive();
        if(current != null && current.leaf.getCore().getPageNumber() == page) return;
        
        List<javafx.scene.control.TreeItem<String>> exercises = GradeTreeView.getTotal().getChildren();
        for(int i = 0; i < exercises.size(); i++){
            GradeTreeItem exerciseItem = (GradeTreeItem) exercises.get(i);
            GradeTreeItem firstOnPage = GradeTreeView.getGradesArray(exerciseItem).stream()
                    .filter(item -> !item.hasSubGrade() && item.getCore().getPageNumber() == page)
                    .findFirst().orElse(null);
            if(firstOnPage == null) continue;
            if(i != MainWindow.footerBar.getSelectedExerciseIndex()){
                MainWindow.footerBar.setSelectedExerciseKey(MainWindow.footerBar.getExerciseKey(i));
                reloadNow();
                ExerciseCorrectionData.requestSave();
            }
            onGradeSelected(firstOnPage);
            return;
        }
    }
    
    // A grade is selected in the tree or on the document: shows its exercise, and activates its sub-grade.
    private void onGradeSelected(GradeTreeItem item){
        if(selectingInTree || item.isRoot() || GradeTreeView.getTotal() == null || MainWindow.footerBar == null) return;
        GradeTreeItem top = item;
        while(top.getParent() instanceof GradeTreeItem parent && parent != GradeTreeView.getTotal()) top = parent;
        int index = GradeTreeView.getTotal().getChildren().indexOf(top);
        if(index < 0) return;
        
        if(index != MainWindow.footerBar.getSelectedExerciseIndex()){
            MainWindow.footerBar.setSelectedExerciseKey(MainWindow.footerBar.getExerciseKey(index));
            reloadNow();
            ExerciseCorrectionData.requestSave();
        }
        if(exercise == null) return;
        String path = item.getCore().getPath();
        for(int i = 0; i < sections.size(); i++){
            String leafPath = sections.get(i).path;
            if(leafPath.equals(path) || leafPath.startsWith(path + "\\")){
                if(i != active) setActive(i, false);
                return;
            }
        }
    }

    public void focusPanel(){
        if(!MainWindow.gradingTab.isSelected()){
            MainWindow.gradingTab.select();
            Platform.runLater(this::focusPanel);
            return;
        }
        if(exercise == null) return;
        if(getActive() == null) general.requestFocus();
        else scroll.requestFocus();
    }

    private void ensureVisible(Node node){
        double contentHeight = sectionsBox.getHeight();
        double viewportHeight = scroll.getViewportBounds().getHeight();
        if(contentHeight <= viewportHeight) return;
        Bounds bounds = node.getBoundsInParent();
        double top = scroll.getVvalue() * (contentHeight - viewportHeight);
        if(bounds.getMinY() < top){
            scroll.setVvalue(bounds.getMinY() / (contentHeight - viewportHeight));
        }else if(bounds.getMaxY() > top + viewportHeight){
            scroll.setVvalue(Math.min(1, (bounds.getMaxY() - viewportHeight) / (contentHeight - viewportHeight)));
        }
    }

    // KEYBOARD (the panel has the focus, not one of its fields)

    private void onPanelKey(KeyEvent e){
        if(e.isShortcutDown() || e.isAltDown()) return; // Global shortcuts
        // The fields of the panel handle their own keys (this filter also receives them)
        if(isInTextField(e.getTarget()) || isInTextField(getScene() == null ? null : getScene().getFocusOwner())) return;
        Section section = getActive();

        Integer number = MathUtils.parseIntFromKeyEventOrNull(e);
        if(number != null && section != null){
            e.consume();
            if(number == 0) section.setTypedPoints("0");
            else if(number <= section.entries.size()) section.toggle(section.entries.get(number - 1));
            return;
        }
        if(("=".equals(e.getText()) || "+".equals(e.getText())) && section != null){
            e.consume();
            section.setTypedPoints(MainWindow.gradesDigFormat.format(section.leaf.getCore().getTotal()));
            return;
        }
        switch(e.getCode()){
            case DOWN, ENTER -> {
                e.consume();
                if(e.getCode() == KeyCode.ENTER && e.isShiftDown()) setActive(active - 1, true);
                else setActive(active + 1, true);
            }
            case UP -> {
                e.consume();
                setActive(active - 1, true);
            }
            case LEFT, RIGHT -> {
                e.consume();
                MainWindow.filesTab.openNeighborFile(e.getCode() == KeyCode.LEFT ? -1 : 1, true);
            }
            case TAB -> {
                e.consume();
                if(section != null) section.points.requestFocus();
                else general.requestFocus();
            }
            case C -> {
                e.consume();
                swallowNextTyped = true;
                if(section != null) section.comment.requestFocus();
                else general.requestFocus();
            }
            case N -> {
                e.consume();
                swallowNextTyped = true;
                if(section != null) section.showNewItem();
            }
            case G -> {
                e.consume();
                swallowNextTyped = true;
                setActive(sections.size(), false);
                general.requestFocus();
            }
            case Z -> {
                e.consume();
                openUngradedCopy(e.isShiftDown() ? -1 : 1);
            }
            case ESCAPE -> {
                e.consume();
                MainWindow.mainScreen.requestFocus();
            }
            case DELETE, BACK_SPACE -> {
                // Deletes the comment selected on the document (e.g. the one just placed)
                if(MainWindow.mainScreen.getSelected() instanceof TextElement text){
                    e.consume();
                    text.delete(true, UType.ELEMENT);
                    refresh();
                }
            }
        }
    }

    // COMMENTS
    // Each sub-grade (and the exercise, for the general comment) has one comment on the copy, linked to its field:
    // the field shows it, editing the field edits it, emptying the field removes it.
    
    // The comment of a grade on the document, if any.
    private static TextElement findComment(String gradePath){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            for(var element : page.getElements()){
                if(element instanceof TextElement text && !(element instanceof ScoredCommentElement)
                        && gradePath.equals(text.getGradeCommentPath())) return text;
            }
        }
        return null;
    }
    private static String getCommentText(GradeTreeItem grade){
        TextElement comment = grade == null ? null : findComment(grade.getCore().getPath());
        return comment == null ? "" : comment.getText();
    }
    
    /**
     * Writes the field in the comment of the grade: creates it next to the grade (or where the user clicks if byClick),
     * updates its text, or removes it if the field is empty. Then runs then.
     */
    private void commitComment(TextField field, GradeTreeItem grade, boolean byClick, Runnable then){
        if(grade == null || !MainWindow.mainScreen.hasDocument(false)){
            if(then != null) then.run();
            return;
        }
        String path = grade.getCore().getPath();
        if(path.equals(pendingPath) && !byClick) return; // Waiting for the click
        String text = field.getText().trim();
        TextElement comment = findComment(path);
        
        if(byClick && !text.isEmpty()){
            pendingText = text;
            pendingPath = path;
            afterPendingPlaced = then;
            refresh();
            return;
        }
        if(text.isEmpty()){
            if(comment != null) comment.delete(true, UType.ELEMENT);
        }else if(comment != null){
            if(!comment.getText().equals(text)) comment.setText(text);
            if(then != null) MainWindow.mainScreen.setSelected(comment); // Validated with Enter: shows it
        }else{
            QuickGradePlacement.placeText(text, QuickGradePlacement.nextSpot(grade.getCore(), getFallbackPageIndex()), path);
        }
        if(then != null) then.run();
    }
    
    // Writes the field being edited (comment or points) before the panel is rebuilt or the document closed.
    public void commitEdits(){
        if(exercise == null || !MainWindow.mainScreen.hasDocument(false)) return;
        for(Section section : sections){
            section.applyPoints();
            if(section.comment.isFocused()) commitComment(section.comment, section.leaf, false, null);
        }
        if(general.isFocused()) commitComment(general, exercise, false, null);
    }
    
    // The comment is written on the copy while it is typed (a short delay after the last key).
    private void setupLiveComment(TextField field, java.util.function.Supplier<GradeTreeItem> grade){
        PauseTransition delay = new PauseTransition(Duration.millis(400));
        delay.setOnFinished(e -> {
            if(field.isFocused()) commitComment(field, grade.get(), false, null);
        });
        field.textProperty().addListener((o, oldValue, newValue) -> {
            if(field.isFocused()) delay.playFromStart();
        });
    }
    
    private void onMainScreenPressed(MouseEvent e){
        if(pendingText == null || e.getButton() != MouseButton.PRIMARY || !MainWindow.mainScreen.hasDocument(false)) return;
        PageRenderer page = MainWindow.mainScreen.document.getLastCursorOverPageObject();
        if(page == null) return;
        e.consume();
        
        int x = page.toGridX(page.getMouseX());
        int y = page.toGridY(page.getMouseY());
        TextElement comment = findComment(pendingPath);
        if(comment != null){ // Moves the existing comment
            if(!comment.getText().equals(pendingText)) comment.setText(pendingText);
            if(comment.getPageNumber() != page.getPage()) comment.switchPage(page.getPage());
            comment.setRealX(x);
            comment.setRealY(y);
            MainWindow.mainScreen.setSelected(comment);
        }else{
            comment = QuickGradePlacement.placeText(pendingText, new QuickGradePlacement.Spot(page, x, y), pendingPath);
        }
        comment.centerOnCoordinatesY();
        
        Runnable then = afterPendingPlaced;
        pendingText = null;
        pendingPath = null;
        afterPendingPlaced = null;
        refresh();
        if(then != null) then.run();
        Platform.runLater(this::focusPanel);
    }
    
    private void cancelPending(){
        if(pendingText == null) return;
        pendingText = null;
        pendingPath = null;
        afterPendingPlaced = null;
        refresh();
    }
    
    private int getFallbackPageIndex(){
        OptionalInt mapped = MainWindow.footerBar.getSelectedExercisePageIndex();
        if(mapped.isPresent()) return mapped.getAsInt();
        PageRenderer page = MainWindow.mainScreen.document.getLastCursorOverPageObject();
        return page == null ? 0 : page.getPage();
    }

    // NEXT UNGRADED COPY

    private void openUngradedCopy(int delta){
        if(exercise == null || !MainWindow.mainScreen.hasDocument(false)) return;
        commitEdits();

        String exercisePath = exercise.getCore().getPath();
        List<File> files = MainWindow.filesTab.files.getItems();
        int current = MainWindow.filesTab.files.getSelectionModel().getSelectedIndex();

        OptionalInt target = ExerciseCorrectionWorkflow.findNeighborFile(files.size(), current, delta, index -> {
            try{
                return !ExerciseCorrectionWorkflow.isExerciseGraded(Edition.loadGradeValues(files.get(index)), exercisePath);
            }catch(Exception ex){
                return true; // An unreadable edition is not graded
            }
        });
        if(target.isEmpty()){
            MainWindow.footerBar.showToast(Color.web("#1b5e20"), Color.WHITE, FooterBar.ToastDuration.MEDIUM, TR.tr("gradingPanel.allGraded", exercise.getCore().getName()));
            return;
        }
        focusOnNextReload = true;
        active = 0;
        exercise = null; // The next copy starts at the first sub-grade
        MainWindow.filesTab.openFileAtExercisePage(files.get(target.getAsInt()));
    }

    // SECTION: one sub-grade

    private class Section extends VBox {
        private final GradeTreeItem leaf;
        private final String path;
        private List<ScoredComment> entries = List.of();

        private final Label name = new Label();
        private final Label base = new Label();
        private final TextField points = new TextField();
        private final Label total = new Label();
        private final VBox items = new VBox(4);
        private final Label addItem = new Label(TR.tr("gradingPanel.addItem"));
        private final HBox newItem;
        private final TextField newItemPoints = new TextField();
        private final TextField newItemText = new TextField();
        private final TextField comment = new TextField();

        Section(GradeTreeItem leaf){
            super(6);
            this.leaf = leaf;
            this.path = leaf.getCore().getPath();
            setPadding(new Insets(8, 10, 10, 10));
            setOnMousePressed(e -> {
                activate(false);
                if(!isInTextField(e.getTarget())) scroll.requestFocus();
            });

            // Header: name, base, points / total
            name.setText(getDisplayName(leaf));
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: " + palette.text() + ";");
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            base.setCursor(Cursor.HAND);
            base.setTooltip(new Tooltip(TR.tr("gradingPanel.base.tooltip")));
            base.setOnMouseClicked(e -> ScoredComments.setBase(path, ScoredComments.getCatalog().getBase(path) == ScoredCommentCatalog.Base.FULL
                    ? ScoredCommentCatalog.Base.ZERO : ScoredCommentCatalog.Base.FULL));
            points.setPrefWidth(52);
            points.setAlignment(Pos.CENTER_RIGHT);
            points.setPromptText("–");
            points.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                switch(e.getCode()){
                    case ENTER -> {
                        e.consume();
                        applyPoints();
                        GradingPanel.this.setActive(sections.indexOf(this) + (e.isShiftDown() ? -1 : 1), true);
                    }
                    case TAB -> {
                        e.consume();
                        applyPoints();
                        if(e.isShiftDown()) scroll.requestFocus();
                        else comment.requestFocus();
                    }
                    case ESCAPE -> {
                        e.consume();
                        syncPoints();
                        scroll.requestFocus();
                    }
                }
            });
            points.focusedProperty().addListener((o, oldValue, newValue) -> {
                if(newValue){
                    activate(false);
                    Platform.runLater(points::selectAll);
                }else applyPoints();
            });
            total.setStyle("-fx-font-weight: bold; -fx-text-fill: " + palette.muted() + ";");
            total.setMinWidth(Region.USE_PREF_SIZE);
            HBox header = new HBox(6, name, base, points, total);
            header.setAlignment(Pos.CENTER_LEFT);

            // New rubric item
            addItem.setCursor(Cursor.HAND);
            addItem.setStyle("-fx-text-fill: " + palette.accent() + "; -fx-font-size: 12;");
            addItem.setOnMouseClicked(e -> showNewItem());
            newItemPoints.setPromptText("-1");
            newItemPoints.setPrefWidth(52);
            newItemText.setPromptText(TR.tr("gradingPanel.newItemText"));
            HBox.setHgrow(newItemText, Priority.ALWAYS);
            for(TextField field : new TextField[]{newItemPoints, newItemText}){
                field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if(e.getCode() == KeyCode.ENTER){
                        e.consume();
                        createItem();
                    }else if(e.getCode() == KeyCode.ESCAPE){
                        e.consume();
                        hideNewItem();
                        scroll.requestFocus();
                    }
                });
            }
            newItem = new HBox(6, newItemPoints, newItemText);
            hideNewItem();

            // Comment on the copy
            comment.setPromptText(TR.tr("gradingPanel.comment"));
            setupLiveComment(comment, () -> leaf);
            comment.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                switch(e.getCode()){
                    case ENTER -> {
                        e.consume();
                        int target = sections.indexOf(this) + (e.isShiftDown() ? -1 : 1);
                        commitComment(comment, leaf, e.isShortcutDown(), () -> GradingPanel.this.setActive(target, true));
                    }
                    case TAB, ESCAPE -> {
                        e.consume();
                        if(pendingText != null) cancelPending();
                        if(e.getCode() == KeyCode.ESCAPE) comment.setText(getCommentText(leaf)); // Cancels the edit
                        else commitComment(comment, leaf, false, null);
                        scroll.requestFocus();
                    }
                }
            });
            comment.focusedProperty().addListener((o, oldValue, newValue) -> {
                if(newValue) activate(false);
                else commitComment(comment, leaf, false, null);
            });

            getChildren().addAll(header, items, addItem, newItem, comment);
            bindValue(leaf.getCore(), this::syncPointsIfNotFocused);
        }

        void update(){
            entries = ScoredComments.getEntriesFor(path);
            boolean fromFull = ScoredComments.getCatalog().getBase(path) == ScoredCommentCatalog.Base.FULL;
            base.setText(TR.tr(fromFull ? "gradingPanel.base.full" : "gradingPanel.base.zero"));
            base.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.muted() + "; -fx-underline: true;");
            total.setText("/ " + MainWindow.gradesDigFormat.format(leaf.getCore().getTotal()));
            syncPointsIfNotFocused();
            if(!comment.isFocused()) comment.setText(getCommentText(leaf));

            items.getChildren().clear();
            boolean isActive = sections.indexOf(this) == active;
            for(int i = 0; i < entries.size(); i++){
                items.getChildren().add(buildItem(entries.get(i), isActive && i < 9 ? String.valueOf(i + 1) : ""));
            }
        }

        private Node buildItem(ScoredComment entry, String key){
            boolean applied = ScoredComments.findPlaced(entry.getId(), path).isPresent();
            Color color = ScoredComments.getColor(entry);
            String web = toWeb(color);

            Label keyLabel = new Label(key);
            keyLabel.setMinWidth(20);
            keyLabel.setAlignment(Pos.CENTER);
            keyLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + palette.muted() + ";"
                    + (key.isEmpty() ? "" : "-fx-border-color: " + palette.border() + "; -fx-border-radius: 3; -fx-padding: 0 4;"));

            Label value = new Label(ScoredCommentGrades.formatPoints(entry.getPoints(), MainWindow.gradesDigFormat));
            value.setMinWidth(Region.USE_PREF_SIZE);
            value.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-padding: 1 7; -fx-background-radius: 10; -fx-background-color: " + web + ";");

            Label text = new Label(entry.getText());
            text.setWrapText(true);
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            text.setStyle("-fx-font-size: 13; -fx-text-fill: " + palette.text() + ";" + (applied ? "-fx-font-weight: bold;" : ""));
            HBox.setHgrow(text, Priority.ALWAYS);

            // Deletes the scored comment from the list (confirmation: the copies where it is placed are listed)
            Button remove = new Button("×");
            remove.setFocusTraversable(false);
            remove.setCursor(Cursor.HAND);
            remove.setTooltip(new Tooltip(TR.tr("gradingPanel.deleteItem")));
            remove.setStyle("-fx-background-color: transparent; -fx-padding: 0 4; -fx-font-size: 14; -fx-text-fill: " + palette.muted() + ";");
            remove.setOnAction(e -> MainWindow.gradeTab.scoredCommentPanel.deleteEntry(entry));
            remove.setOpacity(0);
            
            HBox row = new HBox(8, keyLabel, value, text, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setOnMouseEntered(e -> remove.setOpacity(1));
            row.setOnMouseExited(e -> remove.setOpacity(0));
            MenuItem editItem = new MenuItem(TR.tr("actions.edit"));
            editItem.setOnAction(e -> MainWindow.gradeTab.scoredCommentPanel.editEntry(entry));
            MenuItem deleteItem = new MenuItem(TR.tr("gradingPanel.deleteItem"));
            deleteItem.setOnAction(e -> MainWindow.gradeTab.scoredCommentPanel.deleteEntry(entry));
            ContextMenu menu = new ContextMenu(editItem, deleteItem);
            row.setOnContextMenuRequested(e -> {
                menu.show(row, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            row.setPadding(new Insets(6, 8, 6, 6));
            row.setCursor(Cursor.HAND);
            row.setStyle(applied
                    ? "-fx-background-color: " + toWeb(color.deriveColor(0, 1, 1, .22)) + "; -fx-background-radius: 4; -fx-border-color: " + web + "; -fx-border-radius: 4; -fx-border-width: 1 1 1 4;"
                    : "-fx-background-color: " + palette.card() + "; -fx-background-radius: 4; -fx-border-color: " + palette.border() + "; -fx-border-radius: 4;");
            row.setOnMouseClicked(e -> {
                if(e.getButton() == MouseButton.PRIMARY && !(e.getTarget() instanceof Node node && isInside(node, remove))) toggle(entry);
            });
            return row;
        }

        void markActive(boolean isActive){
            setStyle(isActive
                    ? "-fx-background-color: " + palette.activeBackground() + "; -fx-background-radius: 6; -fx-border-color: " + palette.accent() + "; -fx-border-width: 0 0 0 3;"
                    : "-fx-background-color: transparent;");
            // The keys 1-9 are only shown on the active section
            for(int i = 0; i < items.getChildren().size() && i < entries.size(); i++){
                if(items.getChildren().get(i) instanceof HBox row && row.getChildren().getFirst() instanceof Label key){
                    key.setText(isActive && i < 9 ? String.valueOf(i + 1) : "");
                    key.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + palette.muted() + ";"
                            + (key.getText().isEmpty() ? "" : "-fx-border-color: " + palette.border() + "; -fx-border-radius: 3; -fx-padding: 0 4;"));
                }
            }
        }

        private void activate(boolean focus){
            int index = sections.indexOf(this);
            if(index != active) GradingPanel.this.setActive(index, focus);
        }
        
        void toggle(ScoredComment entry){
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            activate(false);
            ScoredComments.findPlaced(entry.getId(), path).ifPresentOrElse(
                    placed -> placed.delete(true, UType.ELEMENT),
                    () -> {
                        QuickGradePlacement.Spot spot = QuickGradePlacement.nextSpot(leaf.getCore(), getFallbackPageIndex());
                        ScoredCommentElement element = ScoredComments.placeOnGrid(entry, spot.page(), spot.x(), spot.y(), QuickGradePlacement.COLUMN_WIDTH);
                        if(element != null) MainWindow.mainScreen.setSelected(element);
                    });
            refresh();
        }

        void showNewItem(){
            newItem.setVisible(true);
            newItem.setManaged(true);
            addItem.setVisible(false);
            addItem.setManaged(false);
            newItemPoints.requestFocus();
        }
        void hideNewItem(){
            newItemPoints.clear();
            newItemText.clear();
            newItem.setVisible(false);
            newItem.setManaged(false);
            addItem.setVisible(true);
            addItem.setManaged(true);
        }
        private void createItem(){
            String text = newItemText.getText().trim();
            if(text.isEmpty()){
                newItemText.requestFocus();
                return;
            }
            double value;
            try{
                value = Double.parseDouble(newItemPoints.getText().trim().replace(',', '.').replace('−', '-'));
            }catch(NumberFormatException ex){
                newItemPoints.requestFocus();
                return;
            }
            ScoredComment entry = new ScoredComment(path, text, value, null);
            ScoredComments.getCatalog().add(entry);
            ScoredComments.fireChanged(true);
            hideNewItem();
            toggle(entry);
            scroll.requestFocus();
        }

        // Typed points: same rules as the grade field of the tree (a typed value wins over the scored comments).
        void applyPoints(){
            if(leaf.isDeleted()) return;
            if(!points.getText().trim().equals(getValueText())) setTypedPoints(points.getText().trim());
        }
        void setTypedPoints(String text){
            if(leaf.getPanel() == null || leaf.getPanel().gradeField == null) return;
            leaf.getPanel().gradeField.setText(text);
            syncPoints();
        }
        private void syncPointsIfNotFocused(){
            if(!points.isFocused()) syncPoints();
            updateHeader();
        }
        private void syncPoints(){
            points.setText(getValueText());
        }
        private String getValueText(){
            return leaf.getCore().getValue() == -1 ? "" : MainWindow.gradesDigFormat.format(leaf.getCore().getValue());
        }
    }

    private String getDisplayName(GradeTreeItem leaf){
        if(leaf == exercise) return exercise.getCore().getName();
        ArrayList<String> names = new ArrayList<>();
        GradeTreeItem item = leaf;
        while(item != null && item != exercise){
            names.addFirst(item.getCore().getName());
            item = item.getParent() instanceof GradeTreeItem parent ? parent : null;
        }
        return String.join(" › ", names);
    }

    private static boolean isInside(Node node, Node parent){
        while(node != null){
            if(node == parent) return true;
            node = node.getParent();
        }
        return false;
    }
    
    private static boolean isInTextField(Object target){
        Node node = target instanceof Node n ? n : null;
        while(node != null){
            if(node instanceof TextInputControl) return true;
            node = node.getParent();
        }
        return false;
    }
    
    private static String toWeb(Color color){
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.2f)", (int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255), color.getOpacity());
    }
}
