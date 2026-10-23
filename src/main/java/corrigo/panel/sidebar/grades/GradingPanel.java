/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import corrigo.panel.sidebar.grades.tags.TagPicker;
import corrigo.panel.sidebar.grades.lines.LineCorrection;
import corrigo.panel.sidebar.grades.lines.LinesEditor;
import corrigo.panel.sidebar.grades.export.FairnessExport;
import corrigo.panel.sidebar.grades.tags.ExerciseTags;
import corrigo.panel.sidebar.grades.tags.ExerciseTagsCard;
import corrigo.panel.sidebar.grades.tags.EvaluationTags;
import corrigo.datasaving.simpleconfigs.ExerciseCorrectionData;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredComment;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentCatalog;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredComments;
import fr.clementgre.pdf4teachers.utils.MathUtils;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import corrigo.document.editions.elements.ScoredCommentElement;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
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
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Grading panel docked at the right of the document, inspired by Gradescope:
 * all the sub-grades of the selected exercise, with their points and comments, the methods and mistakes of the copy
 * (1-9 add one, possibly with points),
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
    private final ExerciseTagsCard.Colors tagColors = new ExerciseTagsCard.Colors(palette.card(), palette.border(), palette.text(), palette.muted(),
            TagPicker.getColor(EvaluationTags.Kind.METHOD), TagPicker.getColor(EvaluationTags.Kind.MISTAKE));
    // Methods and mistakes of the exercise, after the general comment
    private final ExerciseTagsCard tagsCard = new ExerciseTagsCard(tagColors);
    private final QuickMarksBar quickMarks = new QuickMarksBar(palette.border(), palette.text(), palette.muted(), palette.accent());
    // Grading by lines: the text of the exercise, its marks (tick, cross, presentation)
    private final LinesEditor linesEditor = new LinesEditor(new LinesEditor.Colors(palette.card(), palette.border(), palette.text(), palette.muted(), palette.accent()),
            new QuickMarksBar(palette.border(), palette.text(), palette.muted(), palette.accent()), new LinesEditor.Navigation(){
                @Override
                public void ungradedCopy(int delta){
                    openUngradedCopy(delta);
                }
                @Override
                public void copy(int delta){
                    openNeighborCopy(delta);
                }
                @Override
                public void exercise(int delta){
                    openExercise(delta);
                }
            });
    // The panel shows the grading by lines (setting)
    private final BooleanProperty linesMode = new SimpleBooleanProperty(LineCorrection.isEnabled());

    // HEADER
    private final Label exerciseName = new Label();
    private final Label exerciseScore = new Label();
    private final Label copyInfo = new Label();
    // How the points of the exercise count (by mistakes / by criteria) and why (first point, class, copy): a click changes it
    private final Label pointsMode = new Label();
    private final Button previousExercise = new Button("‹");
    private final Button nextExercise = new Button("›");
    // One button per exercise, to jump to its page
    private final FlowPane exerciseJumps = new FlowPane(4, 4);

    // CONTENT
    private final VBox sectionsBox = new VBox(4);
    private final ScrollPane scroll = new ScrollPane(sectionsBox);
    private final HBox generalBox = new HBox(8);
    private final TextField general = new TextField();
    private CommentSuggestions generalSuggestions;

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
    // Waiting for a click on a page to set the position of the mark; then computes the marks if markThenCompute
    private boolean pendingMark;
    private boolean markThenCompute;

    private boolean focusOnNextReload;
    private final EventHandler<KeyEvent> windowKeyFilter = this::onWindowKey;
    private boolean swallowNextTyped;
    private final PauseTransition followPageDelay = new PauseTransition(Duration.millis(250));
    // After a jump to an exercise, its page may also hold sub-grades of another exercise: the panel must not follow the page.
    private long ignoreFollowPageUntil;

    public GradingPanel(){
        setStyle("-fx-background-color: " + palette.background() + ";");

        getChildren().add(ExerciseTagsCard.createReviewBanner(tagColors));
        setupHeader();
        setupContent();
        setupFooter();


        MainWindow.mainScreen.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMainScreenPressed);
        MainWindow.mainScreen.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if(pendingMark && e.getCode() == KeyCode.ESCAPE){
                e.consume();
                cancelPending();
            }
        });
        MainWindow.mainScreen.isEditPagesModeProperty().addListener((o, oldValue, newValue) -> {
            reload();
            corrigo.panel.sidebar.grades.tags.TagMarkers.update();
        });
        MainWindow.mainScreen.statusProperty().addListener((o, oldValue, newValue) -> reload());
        // The panel follows the page being read
        followPageDelay.setOnFinished(e -> followVisiblePage());
        MainWindow.mainScreen.pane.translateYProperty().addListener((o, oldValue, newValue) -> followPageDelay.playFromStart());
        // The panel follows the grade selected in the grade tree or on the document (a grade being entered)
        MainWindow.gradeTab.treeView.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) -> {
            // Later: the selection also changes while a document is loaded or closed
            if(newValue instanceof GradeTreeItem item){
                if(selectingInTree) return; // Selected by the panel: it already shows it
                Platform.runLater(() -> {
                    if(!item.isDeleted() && MainWindow.gradeTab.treeView.getSelectionModel().getSelectedItem() == item) onGradeSelected(item);
                });
            }
        });
        ScoredComments.revisionProperty().addListener((o, oldValue, newValue) -> scheduleUpdate(false));
        LineCorrection.revisionProperty().addListener((o, oldValue, newValue) -> scheduleUpdate(false));
        if(Main.settings != null){
            Main.settings.gradingByLines.valueProperty().addListener((o, oldValue, newValue) -> reload());
        }
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
        previousExercise.setOnAction(e -> openExercise(-1));
        nextExercise.setOnAction(e -> openExercise(1));

        HBox title = new HBox(2, previousExercise, exerciseName, nextExercise, exerciseScore);
        title.setAlignment(Pos.CENTER_LEFT);
        // Without grade scale, no exercise to show
        title.visibleProperty().bind(exerciseName.textProperty().isNotEmpty());
        title.managedProperty().bind(title.visibleProperty());
        exerciseJumps.setPadding(new Insets(6, 0, 0, 4));
        exerciseJumps.managedProperty().bind(exerciseJumps.visibleProperty());
        pointsMode.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.text() + "; -fx-underline: true;");
        pointsMode.setCursor(Cursor.HAND);
        pointsMode.setWrapText(true); // Long in French: on two lines rather than cut
        pointsMode.setMinHeight(Region.USE_PREF_SIZE);
        pointsMode.setTooltip(new Tooltip(TR.tr("gradingPanel.mode.tooltip")));
        pointsMode.setOnMouseClicked(e -> showModeMenu(pointsMode));
        pointsMode.visibleProperty().bind(title.visibleProperty().and(linesMode.not()));
        pointsMode.managedProperty().bind(pointsMode.visibleProperty());
        VBox header = new VBox(2, title, copyInfo, pointsMode, exerciseJumps);
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
        // Grading by lines: what is typed in the window outside a text field goes to the text of the exercise
        sceneProperty().addListener((o, oldScene, newScene) -> {
            if(oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, windowKeyFilter);
            if(newScene != null) newScene.addEventFilter(KeyEvent.KEY_PRESSED, windowKeyFilter);
        });
        // A click in the empty space gives the keyboard to the panel
        sectionsBox.setOnMousePressed(e -> scroll.requestFocus());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        Label generalTitle = new Label(TR.tr("gradingPanel.generalShort"));
        generalTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: " + palette.text() + ";");
        generalTitle.setMinWidth(Region.USE_PREF_SIZE);
        generalTitle.setTooltip(new Tooltip(TR.tr("gradingPanel.generalTitle")));
        general.setPromptText(TR.tr("gradingPanel.generalTitle"));
        HBox.setHgrow(general, Priority.ALWAYS);
        generalBox.setAlignment(Pos.CENTER_LEFT);
        generalBox.setPadding(new Insets(6, 0, 4, 0));
        general.setOnMousePressed(e -> setActive(sections.size(), false));
        setupLiveComment(general, () -> exercise);
        generalSuggestions = createSuggestions(general, () -> exercise);
        general.focusedProperty().addListener((o, oldValue, newValue) -> {
            if(newValue) showComment(exercise);
            else commitComment(general, exercise, false, null);
        });
        general.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if(generalSuggestions.onKey(e)) return;
            switch(e.getCode()){
                case ENTER -> {
                    e.consume();
                    int delta = e.isShiftDown() ? -1 : 1;
                    if(e.isShortcutDown()) commitComment(general, exercise, true, null);
                    else commitComment(general, exercise, false, () -> openUngradedCopy(delta));
                }
                case ESCAPE -> {
                    e.consume();
                    if(pendingText != null) cancelPending();
                    general.setText(getCommentText(exercise)); // Cancels the edit
                    scroll.requestFocus();
                }
                case TAB -> {
                    e.consume();
                    if(pendingText != null) cancelPending();
                    commitComment(general, exercise, false, null);
                    // Shift+Tab: back to the comment of the last sub-grade; Tab: done with the exercise
                    if(e.isShiftDown()) focusField(sections.size() - 1, true);
                    else scroll.requestFocus();
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
        // ‹ previous ungraded · Next ungraded › · Marks ▾ · ?
        previousUngraded.setText("‹");
        previousUngraded.setTooltip(new Tooltip(TR.tr("gradingPanel.previousUngraded.tooltip")));
        nextUngraded.setText(TR.tr("gradingPanel.nextUngraded"));
        nextUngraded.setTooltip(new Tooltip(TR.tr("gradingPanel.nextUngraded.tooltip")));
        for(Button button : new Button[]{previousUngraded, nextUngraded}) button.setFocusTraversable(false);
        previousUngraded.setOnAction(e -> commitComment(general, exercise, false, () -> openUngradedCopy(-1)));
        nextUngraded.setOnAction(e -> commitComment(general, exercise, false, () -> openUngradedCopy(1)));
        nextUngraded.setMaxWidth(Double.MAX_VALUE);
        nextUngraded.setWrapText(true); // Long labels (French…) go on two lines instead of being cut
        nextUngraded.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nextUngraded.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(nextUngraded, Priority.ALWAYS);
        nextUngraded.setStyle("-fx-background-color: " + palette.accent() + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 10; -fx-background-radius: 4;");
        previousUngraded.setStyle("-fx-padding: 6 10; -fx-background-radius: 4; -fx-font-weight: bold;");
        previousUngraded.setMinWidth(Region.USE_PREF_SIZE);

        // Marks: used once per evaluation, in a menu
        MenuItem markPositionItem = new MenuItem(TR.tr("marks.position"));
        markPositionItem.setOnAction(e -> armMarkPlacement(false));
        MenuItem computeMarksItem = new MenuItem(TR.tr("marks.compute"));
        computeMarksItem.setOnAction(e -> computeMarks());
        // The pages of the exercises, when they are not where their grades are
        MenuItem exercisePagesItem = new MenuItem(TR.tr("gradingPanel.exercisePages"));
        exercisePagesItem.setOnAction(e -> MainWindow.footerBar.editExercisePages());
        MenuItem markScaleItem = new MenuItem(TR.tr("markScale.menu"));
        markScaleItem.setOnAction(e -> MarkScaleDialog.show());
        // All the grading, anonymous, for a review of its fairness (an AI, a colleague)
        MenuItem fairnessItem = new MenuItem(TR.tr("fairness.menu"));
        fairnessItem.setOnAction(e -> exportForReview());
        MenuButton marks = new MenuButton(TR.tr("gradingPanel.moreMenu"), null, markPositionItem, computeMarksItem, markScaleItem,
                new SeparatorMenuItem(), exercisePagesItem, new SeparatorMenuItem(), fairnessItem);
        marks.setFocusTraversable(false);
        marks.setMinWidth(Region.USE_PREF_SIZE);
        marks.setTooltip(new Tooltip(TR.tr("marks.position.tooltip") + "\n" + TR.tr("marks.compute.tooltip") + "\n" + TR.tr("gradingPanel.exercisePages.tooltip")));
        marks.setStyle("-fx-padding: 2 2; -fx-background-radius: 4;");

        // The keys, on demand
        Label keys = new Label("?");
        keys.setMinWidth(Region.USE_PREF_SIZE);
        keys.setStyle("-fx-font-weight: bold; -fx-text-fill: " + palette.muted() + "; -fx-border-color: " + palette.border() + "; -fx-border-radius: 10; -fx-padding: 1 7;");
        Tooltip keysTooltip = new Tooltip(TR.tr("gradingPanel.hint"));
        keysTooltip.setWrapText(true);
        keysTooltip.setMaxWidth(320);
        keysTooltip.setShowDelay(Duration.millis(150));
        keys.setTooltip(keysTooltip);

        // What to do now, only while waiting for a click on the page
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + palette.accent() + ";");
        hint.managedProperty().bind(hint.visibleProperty());

        HBox buttons = new HBox(6, previousUngraded, nextUngraded, marks, keys);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox footer = new VBox(6, hint, buttons);
        footer.setPadding(new Insets(8, 10, 8, 10));
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
        linesMode.set(LineCorrection.isEnabled());
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
            exerciseJumps.getChildren().clear();
            exerciseJumps.setVisible(false);
            tagsCard.setExercise(null);
            linesEditor.setExercise(null);
            Label empty = new Label(TR.tr(MainWindow.mainScreen.hasDocument(false) ? "gradingPanel.noGradeScale" : "gradingPanel.noDocument"));
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: " + palette.muted() + ";");
            sectionsBox.getChildren().add(empty);
            if(MainWindow.mainScreen.hasDocument(false)){
                Button createScale = new Button(TR.tr("gradingPanel.createGradeScale"));
                createScale.setStyle("-fx-background-color: " + palette.accent() + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12; -fx-background-radius: 4;");
                createScale.setOnAction(e -> GradeScaleSetupDialog.show());
                sectionsBox.getChildren().add(createScale);
            }else{
                Button newEvaluation = new Button(TR.tr("menuBar.file.newEvaluation"));
                newEvaluation.setStyle("-fx-background-color: " + palette.accent() + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12; -fx-background-radius: 4;");
                newEvaluation.setOnAction(e -> NewEvaluationWizard.show());
                sectionsBox.getChildren().add(newEvaluation);
            }
            return;
        }

        // The pages of the exercises are not known: says it, with the way to set them
        if(MainWindow.footerBar.areExercisePagesUnknown()){
            Label unknown = new Label(TR.tr("gradingPanel.exercisePages.unknown"));
            unknown.setWrapText(true);
            unknown.setStyle("-fx-text-fill: " + palette.text() + "; -fx-font-size: 12;");
            Button setPages = new Button(TR.tr("gradingPanel.exercisePages"));
            setPages.setOnAction(e -> MainWindow.footerBar.editExercisePages());
            VBox banner = new VBox(6, unknown, setPages);
            banner.setPadding(new Insets(8));
            banner.setStyle("-fx-background-color: rgba(255,179,0,.18); -fx-border-color: rgba(255,179,0,.7); -fx-border-radius: 4; -fx-background-radius: 4;");
            sectionsBox.getChildren().add(banner);
        }

        List<GradeTreeItem> leaves = exercise.hasSubGrade()
                ? GradeTreeView.getGradesArray(exercise).stream().filter(item -> !item.hasSubGrade()).toList()
                : List.of(exercise);
        for(GradeTreeItem leaf : leaves){
            Section section = new Section(leaf);
            sections.add(section);
            sectionsBox.getChildren().add(section);
        }
        if(linesMode.get()){ // The text of the exercise replaces the comments, the methods and the mistakes
            sectionsBox.getChildren().add(linesEditor);
            linesEditor.setExercise(exercise);
            tagsCard.setExercise(null);
        }else{
            linesEditor.setExercise(null);
            sectionsBox.getChildren().addAll(generalBox, quickMarks, tagsCard);
            tagsCard.setExercise(exercise);
        }
        // Without sub-grades, the comment of the exercise is its general comment: one field only
        generalBox.setVisible(hasGeneral() && !linesMode.get());
        generalBox.setManaged(generalBox.isVisible());
        active = Math.clamp(active, 0, getLastIndex());

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
        if(linesMode.get()) linesEditor.refresh();
        else tagsCard.refreshScores(); // The points of the copy may have changed
        if(!general.isFocused()) general.setText(getCommentText(exercise));
        updateActiveStyle();
        hint.setVisible(pendingMark || pendingText != null);
        hint.setText(TR.tr(pendingMark ? "marks.hint.clickToPlace" : "gradingPanel.hint.clickToPlace"));
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
        // Position of the open copy in the list (the selection of the list may be empty)
        int file = MainWindow.filesTab.files.getItems().indexOf(MainWindow.mainScreen.document.getFile());
        copyInfo.setText(TR.tr("gradingPanel.copy", MainWindow.mainScreen.document.getFileName(), String.valueOf(file + 1), String.valueOf(files)));
        updatePointsMode();
        updateExerciseJumps();
    }

    // MODE OF THE EXERCISE

    private void updatePointsMode(){
        String path = exercise.getCore().getPath();
        ScoredCommentCatalog.Base copyMode = ScoredComments.getCopyMode(path);
        ScoredCommentCatalog.Base classMode = ScoredComments.getClassMode(path);
        ScoredCommentCatalog.Base autoMode = ScoredComments.getAutoMode(path);
        ScoredCommentCatalog.Base mode = copyMode != null ? copyMode : classMode != null ? classMode : autoMode;
        if(mode == null){ // Automatic, nothing placed yet
            pointsMode.setText(TR.tr("gradingPanel.mode.auto"));
            return;
        }
        pointsMode.setText(TR.tr(mode == ScoredCommentCatalog.Base.ZERO ? "gradingPanel.mode.criteria" : "gradingPanel.mode.mistakes")
                + TR.tr(copyMode != null ? "gradingPanel.mode.fromCopy" : classMode != null ? "gradingPanel.mode.fromClass" : "gradingPanel.mode.fromFirst"));
    }
    // Automatic (the first point placed decides), by mistakes or by criteria, for the class or for this copy only
    private void showModeMenu(javafx.scene.Node anchor){
        if(exercise == null) return;
        String path = exercise.getCore().getPath();
        ScoredCommentCatalog.Base classMode = ScoredComments.getClassMode(path);
        ScoredCommentCatalog.Base copyMode = ScoredComments.getCopyMode(path);
        ToggleGroup classGroup = new ToggleGroup(), copyGroup = new ToggleGroup();
        RadioMenuItem classAuto = new RadioMenuItem(TR.tr("gradingPanel.mode.class.auto"));
        RadioMenuItem classMistakes = new RadioMenuItem(TR.tr("gradingPanel.mode.class.mistakes"));
        RadioMenuItem classCriteria = new RadioMenuItem(TR.tr("gradingPanel.mode.class.criteria"));
        RadioMenuItem copyAsClass = new RadioMenuItem(TR.tr("gradingPanel.mode.copy.class"));
        RadioMenuItem copyMistakes = new RadioMenuItem(TR.tr("gradingPanel.mode.copy.mistakes"));
        RadioMenuItem copyCriteria = new RadioMenuItem(TR.tr("gradingPanel.mode.copy.criteria"));
        for(RadioMenuItem item : new RadioMenuItem[]{classAuto, classMistakes, classCriteria}) item.setToggleGroup(classGroup);
        (classMode == null ? classAuto : classMode == ScoredCommentCatalog.Base.ZERO ? classCriteria : classMistakes).setSelected(true);
        for(RadioMenuItem item : new RadioMenuItem[]{copyAsClass, copyMistakes, copyCriteria}) item.setToggleGroup(copyGroup);
        (copyMode == null ? copyAsClass : copyMode == ScoredCommentCatalog.Base.ZERO ? copyCriteria : copyMistakes).setSelected(true);
        classAuto.setOnAction(e -> ScoredComments.setClassMode(path, null));
        classMistakes.setOnAction(e -> ScoredComments.setClassMode(path, ScoredCommentCatalog.Base.FULL));
        classCriteria.setOnAction(e -> ScoredComments.setClassMode(path, ScoredCommentCatalog.Base.ZERO));
        copyAsClass.setOnAction(e -> ScoredComments.setCopyMode(path, null));
        copyMistakes.setOnAction(e -> ScoredComments.setCopyMode(path, ScoredCommentCatalog.Base.FULL));
        copyCriteria.setOnAction(e -> ScoredComments.setCopyMode(path, ScoredCommentCatalog.Base.ZERO));
        ContextMenu menu = new ContextMenu(classAuto, classMistakes, classCriteria, new SeparatorMenuItem(), copyAsClass, copyMistakes, copyCriteria);
        menu.setOnHidden(e -> scheduleUpdate(false));
        javafx.geometry.Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        menu.show(anchor, bounds.getMinX(), bounds.getMaxY());
    }
    
    private void updateExerciseJumps(){
        exerciseJumps.getChildren().clear();
        List<javafx.scene.control.TreeItem<String>> exercises = GradeTreeView.getTotal().getChildren();
        exerciseJumps.setVisible(exercises.size() > 1);
        int selected = MainWindow.footerBar.getSelectedExerciseIndex();
        for(int i = 0; i < exercises.size(); i++){
            GradeTreeItem item = (GradeTreeItem) exercises.get(i);
            OptionalInt page = MainWindow.footerBar.getExercisePage(i);
            boolean graded = isGraded(item);
            
            Button button = new Button((graded ? "✓ " : "") + item.getCore().getName() + (page.isPresent() ? "  p." + (page.getAsInt() + 1) : ""));
            button.setFocusTraversable(false);
            button.setCursor(Cursor.HAND);
            String colors = i == selected
                    ? "-fx-background-color: " + palette.accent() + "; -fx-text-fill: white;"
                    : "-fx-background-color: " + palette.card() + "; -fx-border-color: " + palette.border() + "; -fx-border-radius: 4; -fx-text-fill: " + (graded ? palette.muted() : palette.text()) + ";";
            button.setStyle("-fx-font-size: 11; -fx-padding: 2 7; -fx-background-radius: 4; " + colors);
            button.setTooltip(new Tooltip(TR.tr(page.isPresent() ? "gradingPanel.jump.tooltip" : "gradingPanel.jump.noPage.tooltip", item.getCore().getName()) + (i < 9 ? " (Alt+" + (i + 1) + ")" : "")));
            int index = i;
            button.setOnAction(e -> jumpToExercise(index));
            exerciseJumps.getChildren().add(button);
        }
    }
    private static boolean isGraded(GradeTreeItem exercise){
        if(!exercise.hasSubGrade()) return exercise.getCore().getValue() != -1;
        return GradeTreeView.getGradesArray(exercise).stream().filter(item -> !item.hasSubGrade()).allMatch(item -> item.getCore().getValue() != -1);
    }
    
    // Selects the exercise and scrolls to its page.
    public boolean jumpToExercise(int index){
        if(MainWindow.mainScreen.isEditPagesMode()) return false;
        commitEdits();
        ignoreFollowPageUntil = System.currentTimeMillis() + 1500;
        return MainWindow.footerBar.goToExercise(index);
    }

    private static String formatScore(GradeElement grade){
        String value = grade.getValue() == -1 ? "–" : MainWindow.gradesDigFormat.format(grade.getValue());
        return value + " / " + MainWindow.gradesDigFormat.format(grade.getTotal());
    }

    // ACTIVE SECTION

    // Path of the active sub-grade if the panel shows this exercise, else null.
    public String getActiveLeafPath(String exerciseName){
        if(exercise == null || !exercise.getCore().getName().equals(exerciseName)) return null;
        Section section = getActive();
        return (section == null ? sections.isEmpty() ? exercise : sections.getFirst().leaf : section.leaf).getCore().getPath();
    }

    // The general comment is the one of the exercise: it has its own field only when the exercise has sub-grades
    private boolean hasGeneral(){
        return exercise != null && exercise.hasSubGrade();
    }
    // Index of the last field group: the general comment, or the last sub-grade
    private int getLastIndex(){
        return hasGeneral() ? sections.size() : Math.max(0, sections.size() - 1);
    }

    private Section getActive(){
        return active < sections.size() ? sections.get(active) : null;
    }

    private void setActive(int index, boolean focus){
        if(exercise == null) return;
        Section old = getActive();
        if(old != null) old.applyPoints();
        cancelPending();
        active = Math.clamp(index, 0, getLastIndex());
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
        if(System.currentTimeMillis() < ignoreFollowPageUntil) return;
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
        if(linesMode.get()) linesEditor.focusArea();
        else if(getActive() == null) general.requestFocus();
        else scroll.requestFocus();
    }

    // Tab / Shift+Tab: points and comment of each sub-grade, then the general comment.
    // index: section (sections.size() for the general comment); comment: its comment field, or else its points.
    private void focusField(int index, boolean comment){
        if(exercise == null || sections.isEmpty()) return;
        if(index > getLastIndex()){ // Tab after the last field: done with the exercise
            scroll.requestFocus();
            return;
        }
        index = Math.clamp(index, 0, getLastIndex());
        if(index != active) setActive(index, false);
        Section section = getActive();
        TextField target = section == null ? general : comment ? section.comment : section.points;
        target.requestFocus();
        if(section == null || target != section.points) target.end();
    }

    private CommentSuggestions createSuggestions(TextField field, java.util.function.Supplier<GradeTreeItem> grade){
        return new CommentSuggestions(field, () -> {
            GradeTreeItem item = grade.get();
            if(item == null || exercise == null) return null;
            return new CommentSuggestions.Grade(item.getCore().getPath(), exercise.getCore().getName());
        }, text -> commitComment(field, grade.get(), false, null), palette.card(), palette.border(), palette.text(), palette.muted());
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

    // KEYBOARD

    // Grading by lines: a letter or a number typed outside a text field (a text of the copy just selected or moved,
    // the page, a button of the panel) goes to the text of the exercise, on a new line: what is typed is never lost
    // nor taken by a shortcut of the old mode (C, G, Z, digits)
    private void onWindowKey(KeyEvent e){
        if(!linesMode.get() || exercise == null || MainWindow.gradingTab == null || !MainWindow.gradingTab.isSelected()) return;
        if(e.isShortcutDown() || e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
        String typed = e.getText();
        if(typed.isBlank() || Character.isISOControl(typed.charAt(0))) return; // Space stays for the buttons
        if(isInTextField(getScene().getFocusOwner())) return;
        linesEditor.focusAreaNow(); // Its character (KEY_TYPED) then reaches the text
    }

    // The panel has the focus, not one of its fields

    private void onPanelKey(KeyEvent e){
        if(e.isShortcutDown() || e.isAltDown()) return; // Global shortcuts
        // The fields of the panel handle their own keys (this filter also receives them)
        if(isInTextField(e.getTarget()) || isInTextField(getScene() == null ? null : getScene().getFocusOwner())) return;
        Section section = getActive();
        if(linesMode.get() && e.getCode() == KeyCode.BACK_SPACE) return; // No method nor mistake to remove

        Integer number = MathUtils.parseDigitTypedOrNull(e);
        if(number != null && section != null){
            e.consume();
            if(number == 0) section.setTypedPoints("0");
            else addTag(number); // The n-th method or mistake of the card, on the active sub-grade
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
                if(section == null) general.requestFocus();
                else if(e.isShiftDown()) section.comment.requestFocus();
                else section.points.requestFocus();
            }
            case C -> {
                e.consume();
                swallowNextTyped = true;
                if(section != null) section.comment.requestFocus();
                else general.requestFocus();
            }
            case G -> {
                e.consume();
                swallowNextTyped = true;
                setActive(getLastIndex(), false);
                if(getActive() == null) general.requestFocus();
                else getActive().comment.requestFocus();
            }
            case Z -> {
                e.consume();
                openUngradedCopy(e.isShiftDown() ? -1 : 1);
            }
            case ESCAPE -> {
                e.consume();
                MainWindow.mainScreen.requestFocus();
            }
            case BACK_SPACE -> {
                e.consume();
                removeLastTag();
            }
            case DELETE -> {
                // Deletes the comment selected on the document (e.g. the one just placed)
                if(MainWindow.mainScreen.getSelected() instanceof TextElement text){
                    e.consume();
                    text.delete(true, UType.ELEMENT);
                    refresh();
                }
            }
        }
    }

    // METHODS AND MISTAKES

    // Keys 1-9: adds an occurrence of the n-th method or mistake of the card to the copy, on the active sub-grade.
    private void addTag(int number){
        if(exercise == null) return;
        String exerciseName = exercise.getCore().getName();
        List<corrigo.panel.sidebar.grades.tags.EvaluationTags.Tag> tags =
                corrigo.panel.sidebar.grades.tags.ExerciseTagsCard.getOrderedTags(exerciseName);
        if(number > tags.size()) return;
        corrigo.panel.sidebar.grades.tags.ExerciseTagsCard.addToOpenCopy(exerciseName, tags.get(number - 1));
    }
    // Backspace: removes the last method or mistake added to the copy for this exercise.
    private void removeLastTag(){
        String copy = corrigo.panel.sidebar.grades.tags.ExerciseTags.getOpenCopy();
        if(exercise == null || copy == null) return;
        String exerciseName = exercise.getCore().getName();
        corrigo.panel.sidebar.grades.tags.ExerciseTags.getData().getUses(copy).stream()
                .filter(use -> use.exercise().equals(exerciseName))
                .reduce((first, second) -> second)
                .ifPresent(use -> corrigo.panel.sidebar.grades.tags.ExerciseTags.removeOccurrence(copy, use.id()));
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
            QuickGradePlacement.Spot spot = grade == exercise && hasGeneral() ? getGeneralSpot()
                    : QuickGradePlacement.nextSpot(grade.getCore(), getFallbackPageIndex());
            QuickGradePlacement.placeText(text, spot, path);
        }
        if(field.isFocused()) showComment(grade); // Seen while it is typed
        if(then != null) then.run();
    }
    
    // Where the general comment of the exercise goes: next to the grade of the exercise if it is on the page of its
    // sub-grades, else under the last sub-grade (the grade of the exercise is often in a table on the first page).
    private QuickGradePlacement.Spot getGeneralSpot(){
        GradeElement last = sections.stream().map(section -> section.leaf.getCore())
                .filter(grade -> grade.getPage() != null)
                .max(java.util.Comparator.comparingInt(GradeElement::getPageNumber).thenComparingInt(GradeElement::getRealY))
                .orElse(null);
        GradeElement exerciseGrade = exercise.getCore();
        if(last == null || exerciseGrade.getPage() != null && exerciseGrade.getPageNumber() == last.getPageNumber()){
            return QuickGradePlacement.nextSpot(exerciseGrade, getFallbackPageIndex());
        }
        return QuickGradePlacement.spotBelow(last, getFallbackPageIndex());
    }
    
    // As in the Texts tab, the comment being edited is selected on the copy, which scrolls to it if it is out of sight.
    private void showComment(GradeTreeItem grade){
        TextElement comment = grade == null ? null : findComment(grade.getCore().getPath());
        if(comment == null) return;
        if(MainWindow.mainScreen.getSelected() != comment) MainWindow.mainScreen.setSelected(comment);
        Platform.runLater(() -> scrollToIfHidden(comment));
    }
    private void scrollToIfHidden(TextElement comment){
        PageRenderer page = comment.getPage();
        if(page == null || comment.getScene() == null) return;
        var zoom = MainWindow.mainScreen.zoomOperator;
        Bounds bounds = comment.localToScene(comment.getBoundsInLocal());
        double screenTop = MainWindow.mainScreen.localToScene(0, 0).getY(); // The visible part of the document
        if(bounds.getMinY() >= screenTop && bounds.getMaxY() <= screenTop + zoom.getMainScreenHeight()) return;
        // Moves the document so that the comment is in the middle of the screen
        double offset = (bounds.getMinY() + bounds.getMaxY()) / 2 - (screenTop + zoom.getMainScreenHeight() / 2);
        double target = MainWindow.mainScreen.pane.getTranslateY() - offset;
        target = Math.clamp(target, -zoom.getScrollableHeight() + zoom.getPaneShiftY(), zoom.getPaneShiftY());
        ignoreFollowPageUntil = System.currentTimeMillis() + 1500; // Stays on this exercise
        zoom.scrollByTranslateY(target, false);
        MainWindow.mainScreen.document.updateShowsStatus();
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
        if(pendingText == null && !pendingMark && isPlacingLines(e)){
            PageRenderer page = MainWindow.mainScreen.document.getLastCursorOverPageObject();
            if(page == null) return;
            e.consume();
            linesEditor.onPageClicked(page, page.toGridX(page.getMouseX()), page.toGridY(page.getMouseY()));
            return;
        }
        if((pendingText == null && !pendingMark) || e.getButton() != MouseButton.PRIMARY || !MainWindow.mainScreen.hasDocument(false)) return;
        PageRenderer page = MainWindow.mainScreen.document.getLastCursorOverPageObject();
        if(page == null) return;
        e.consume();
        
        int x = page.toGridX(page.getMouseX());
        int y = page.toGridY(page.getMouseY());
        if(pendingMark){
            onMarkPlaced(page, x, y);
            return;
        }
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
    
    // A click on the free space of a page places the next line (a click on a text or a mark is for this element)
    private boolean isPlacingLines(MouseEvent e){
        if(!linesMode.get() || e.getButton() != MouseButton.PRIMARY || !MainWindow.mainScreen.hasDocument(false)) return false;
        if(!MainWindow.gradingTab.isSelected() || MainWindow.mainScreen.getToPlace() != null || corrigo.panel.sidebar.grades.tags.TagPlacing.isArmed()) return false;
        if(MainWindow.mainScreen.isEditPagesMode() || !linesEditor.isWaitingForClick()) return false;
        Node node = e.getTarget() instanceof Node target ? target : null;
        while(node != null){
            if(node instanceof Element) return false;
            if(node instanceof PageRenderer) return true;
            node = node.getParent();
        }
        return false;
    }
    // The copy is closed: in the grading by lines, the lines not placed yet are written next to their grades
    public void beforeCopyClosed(){
        if(LineCorrection.isAutoPlace()) LineCorrection.placeAllNextToGrades();
    }

    private void cancelPending(){
        if(pendingText == null && !pendingMark) return;
        pendingMark = false;
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

    // MARKS
    
    // The next click on a page sets the position of the mark of the evaluation.
    private void armMarkPlacement(boolean thenCompute){
        if(exercise == null || !MainWindow.mainScreen.hasDocument(false)) return;
        commitEdits();
        cancelPending();
        pendingMark = true;
        markThenCompute = thenCompute;
        refresh();
        MainWindow.mainScreen.requestFocus();
    }
    
    private void onMarkPlaced(PageRenderer page, int x, int y){
        boolean thenCompute = markThenCompute;
        pendingMark = false;
        markThenCompute = false;
        MarksComputation.setPosition(page, x, y);
        refresh();
        if(thenCompute) computeMarks();
        else MainWindow.footerBar.showToast(Color.web("#1b5e20"), Color.WHITE, FooterBar.ToastDuration.MEDIUM, TR.tr("marks.positionSaved"));
    }
    
    private void computeMarks(){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        commitEdits();
        cancelPending();
        if(!MarksComputation.computeAll()) armMarkPlacement(true); // Where does the mark go?
    }

    // The grading of all the copies of the evaluation, anonymous (FairnessExport), and the table of the names apart
    private void exportForReview(){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        commitEdits();
        MainWindow.mainScreen.document.edition.save(false); // The open copy as it is now
        List<File> pdfs = new ArrayList<>(ExerciseTags.getFolderCopies());
        pdfs.sort(Comparator.comparing(File::getName));
        List<Map<String, Object>> copies = new ArrayList<>();
        try{
            for(File pdf : pdfs){
                File editFile = Edition.getEditFile(pdf);
                Map<String, Object> base = new LinkedHashMap<>();
                if(editFile.exists()){
                    Config config = new Config(editFile);
                    config.load();
                    base = config.base;
                }
                copies.add(base);
            }
            FileChooser chooser = new FileChooser();
            File folder = pdfs.isEmpty() ? null : pdfs.getFirst().getParentFile();
            if(folder != null) chooser.setInitialDirectory(folder);
            chooser.setInitialFileName(TR.tr("fairness.fileName"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
            File out = chooser.showSaveDialog(Main.window);
            if(out == null) return;
            if(!out.getName().toLowerCase(Locale.ROOT).endsWith(".md")) out = new File(out.getAbsolutePath() + ".md");
            String text = FairnessExport.build(copies, PNFAnnotationManager.label(), MainWindow.gradesDigFormat, TR::tr);
            Files.writeString(out.toPath(), text, StandardCharsets.UTF_8);
            StringBuilder table = new StringBuilder("numero;copie\n");
            for(int i = 0; i < copies.size(); i++) table.append(FairnessExport.number(i)).append(';').append(pdfs.get(i).getName()).append('\n');
            File tableFile = new File(out.getParentFile(), out.getName().replaceFirst("\\.md$", "") + TR.tr("fairness.tableSuffix"));
            Files.writeString(tableFile.toPath(), table.toString(), StandardCharsets.UTF_8);
            MainWindow.footerBar.showToast(Color.web("#1b5e20"), Color.WHITE, FooterBar.ToastDuration.LONG,
                    TR.tr("fairness.done").replace("{0}", String.valueOf(copies.size())).replace("{1}", tableFile.getName()));
        }catch(Exception ex){
            Log.eNotified(ex, "Unable to write the anonymous export");
            MainWindow.footerBar.showToast(Color.web("#b71c1c"), Color.WHITE, FooterBar.ToastDuration.LONG,
                    TR.tr("fairness.error").replace("{0}", String.valueOf(ex.getMessage())));
        }
    }

    // NEXT UNGRADED COPY

    // The previous (-1) or next (1) exercise of the copy, the panel ready to type once it shows it
    private void openExercise(int delta){
        focusOnNextReload = true;
        if(!MainWindow.footerBar.selectExercise(delta)) focusOnNextReload = false;
    }
    // The previous (-1) or next (1) copy of the list, on the same exercise, the panel ready to type once it shows it
    private void openNeighborCopy(int delta){
        commitEdits();
        focusOnNextReload = true;
        MainWindow.filesTab.openNeighborFile(delta, true);
    }
    // Grading by lines: time of the warning about the lines not placed (the same key again within 5 s leaves anyway)
    private long unplacedWarningTime;
    private void openUngradedCopy(int delta){
        if(exercise == null || !MainWindow.mainScreen.hasDocument(false)) return;
        commitEdits();
        // Each line is placed with a click: lines not placed yet keep the copy open, once
        if(linesMode.get() && !LineCorrection.isAutoPlace()){
            Map<String, Integer> unplaced = LineCorrection.countUnplaced();
            if(!unplaced.isEmpty() && System.currentTimeMillis() - unplacedWarningTime > 5000){
                unplacedWarningTime = System.currentTimeMillis();
                int count = unplaced.values().stream().mapToInt(Integer::intValue).sum();
                MainWindow.footerBar.showToast(Color.web("#e65100"), Color.WHITE, FooterBar.ToastDuration.LONG,
                        TR.tr("lines.unplacedWarning", String.valueOf(count), String.join(", ", unplaced.keySet())));
                linesEditor.focusArea();
                return;
            }
        }
        unplacedWarningTime = 0;

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

        private final Label name = new Label();
        private final Label base = new Label();
        private final TextField points = new TextField();
        private final Label total = new Label();
        // The comment of an inactive sub-grade, on one line (its field is shown when the sub-grade is active)
        private final Label commentPreview = new Label();
        private final TextField comment = new TextField();
        private final CommentSuggestions suggestions;
        // Grading by lines: where the points start from, or the grade typed by hand (a click: back to the lines)
        private final Label linesInfo = new Label();

        Section(GradeTreeItem leaf){
            super(4);
            this.leaf = leaf;
            this.path = leaf.getCore().getPath();
            setPadding(new Insets(5, 10, 6, 10));
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
            base.setOnMouseClicked(e -> showModeMenu(base));
            points.setPrefWidth(64); // The digits and the clear button of the field
            points.setMinWidth(Region.USE_PREF_SIZE);
            points.setAlignment(Pos.CENTER); // Right-aligned, the digits would be under the clear button of the focused field
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
                        int index = sections.indexOf(this);
                        // Shift+Tab: comment of the previous sub-grade (from the first one: the panel)
                        if(!e.isShiftDown()) comment.requestFocus();
                        else if(index > 0) focusField(index - 1, true);
                        else scroll.requestFocus();
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
            linesInfo.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.muted() + ";");
            linesInfo.setMinWidth(Region.USE_PREF_SIZE);
            linesInfo.managedProperty().bind(linesInfo.visibleProperty());
            linesInfo.setOnMouseClicked(e -> {
                if(exercise != null && LineCorrection.getState(exercise, leaf).typed()) LineCorrection.resetToLines(exercise, leaf);
            });
            HBox header = new HBox(6, name, base, linesInfo, points, total);
            header.setAlignment(Pos.CENTER_LEFT);

            // Comment on the copy
            comment.setPromptText(TR.tr("gradingPanel.comment"));
            setupLiveComment(comment, () -> leaf);
            suggestions = createSuggestions(comment, () -> leaf);
            comment.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if(suggestions.onKey(e)) return;
                switch(e.getCode()){
                    case ENTER -> {
                        e.consume();
                        int target = sections.indexOf(this) + (e.isShiftDown() ? -1 : 1);
                        if(target > getLastIndex() && !e.isShortcutDown()){ // No general comment: next ungraded copy
                            commitComment(comment, leaf, false, () -> openUngradedCopy(1));
                        }else{
                            commitComment(comment, leaf, e.isShortcutDown(), () -> GradingPanel.this.setActive(target, true));
                        }
                    }
                    case ESCAPE -> {
                        e.consume();
                        if(pendingText != null) cancelPending();
                        comment.setText(getCommentText(leaf)); // Cancels the edit
                        scroll.requestFocus();
                    }
                    case TAB -> {
                        e.consume();
                        if(pendingText != null) cancelPending();
                        commitComment(comment, leaf, false, null);
                        // Tab: points of the next sub-grade (after the last one: the general comment); Shift+Tab: points of this one
                        int index = sections.indexOf(this);
                        if(e.isShiftDown()) focusField(index, false);
                        else focusField(index + 1, false);
                    }
                }
            });
            comment.focusedProperty().addListener((o, oldValue, newValue) -> {
                if(newValue){
                    activate(false);
                    showComment(leaf);
                }else commitComment(comment, leaf, false, null);
                updateCompact();
            });
            comment.managedProperty().bind(comment.visibleProperty());
            commentPreview.textProperty().bind(comment.textProperty());
            commentPreview.setStyle("-fx-font-size: 12; -fx-text-fill: " + palette.muted() + ";");
            commentPreview.setMinWidth(0);
            commentPreview.setCursor(Cursor.TEXT);
            commentPreview.managedProperty().bind(commentPreview.visibleProperty());
            commentPreview.setOnMousePressed(e -> { // Edits it right away
                e.consume();
                activate(false);
                comment.setVisible(true);
                comment.requestFocus();
                comment.end();
            });
            base.managedProperty().bind(base.visibleProperty());

            getChildren().addAll(header, comment, commentPreview);
            bindValue(leaf.getCore(), this::syncPointsIfNotFocused);
        }

        void update(){
            updateLinesInfo();
            updateBase();
            base.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.muted() + "; -fx-underline: true;");
            total.setText("/ " + MainWindow.gradesDigFormat.format(leaf.getCore().getTotal()));
            syncPointsIfNotFocused();
            if(!comment.isFocused()) comment.setText(getCommentText(leaf));
            updateCompact();
        }

        // Inactive: one line (name, points), its comment as a line of text if any; "from max" on the active one only.
        private boolean shownActive;
        void updateCompact(){
            if(linesMode.get()){ // One line: name, start, points; the text of the exercise is below
                comment.setVisible(false);
                commentPreview.setVisible(false);
                base.setVisible(false);
                return;
            }
            boolean editing = shownActive || comment.isFocused();
            comment.setVisible(editing);
            commentPreview.setVisible(!editing && !comment.getText().isBlank());
            base.setVisible(shownActive); // How the points of the methods and mistakes count
        }
        private void updateLinesInfo(){
            linesInfo.setVisible(linesMode.get() && exercise != null);
            if(!linesInfo.isVisible()) return;
            LineCorrection.LeafState state = LineCorrection.getState(exercise, leaf);
            double totalValue = leaf.getCore().getTotal();
            if(state.typed()){
                double value = leaf.getCore().getValue();
                linesInfo.setText(TR.tr("lines.typed", value < 0 ? "–" : MainWindow.gradesDigFormat.format(value),
                        state.linesGrade() == null ? TR.tr("lines.block.notGraded") : MainWindow.gradesDigFormat.format(state.linesGrade())));
                linesInfo.setStyle("-fx-font-size: 11; -fx-underline: true; -fx-text-fill: #e65100;");
                linesInfo.setCursor(Cursor.HAND);
                linesInfo.setTooltip(new Tooltip(TR.tr("lines.typed.tooltip")));
            }else{
                linesInfo.setText(state.start() == null ? "" : state.start() >= totalValue ? TR.tr("lines.block.fromMax") : TR.tr("lines.block.fromZero"));
                linesInfo.setStyle("-fx-font-size: 11; -fx-text-fill: " + palette.muted() + ";");
                linesInfo.setCursor(Cursor.DEFAULT);
                linesInfo.setTooltip(null);
            }
        }

        void markActive(boolean isActive){
            shownActive = isActive;
            updateCompact();
            setStyle(isActive
                    ? "-fx-background-color: " + palette.activeBackground() + "; -fx-background-radius: 6; -fx-border-color: " + palette.accent() + "; -fx-border-width: 0 0 0 3;"
                    : "-fx-background-color: transparent;");
        }

        private void activate(boolean focus){
            int index = sections.indexOf(this);
            if(index != active) GradingPanel.this.setActive(index, focus);
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
        // Where the points of the methods and mistakes start from: the typed value, the maximum or 0
        private void updateBase(){
            double start = ScoredComments.getStartShown(path);
            double total = leaf.getCore().getTotal();
            base.setText(start >= total ? TR.tr("gradingPanel.base.full") : start <= 0 ? TR.tr("gradingPanel.base.zero")
                    : TR.tr("gradingPanel.base.typed", MainWindow.gradesDigFormat.format(start)));
        }
        private void syncPointsIfNotFocused(){
            updateBase(); // A typed value changes it
            updateLinesInfo();
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
