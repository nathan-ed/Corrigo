/*
 * Copyright (c) 2019-2022. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 * Modified by Nathan, 2026.
 */

package fr.clementgre.pdf4teachers.panel;

import fr.clementgre.pdf4teachers.components.SliderWithoutPopup;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import corrigo.datasaving.simpleconfigs.ExerciseCorrectionData;
import fr.clementgre.pdf4teachers.panel.MainScreen.MainScreen;
import fr.clementgre.pdf4teachers.panel.MainScreen.ZoomOperator;
import corrigo.panel.sidebar.grades.ExerciseCorrectionWorkflow;
import corrigo.panel.sidebar.grades.ExercisePageMapping;
import corrigo.panel.sidebar.grades.ExercisePageMappingDialog;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredComments;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import corrigo.panel.sidebar.grades.GradingPanel;
import fr.clementgre.pdf4teachers.utils.PlatformUtils;
import fr.clementgre.pdf4teachers.utils.panes.PaneUtils;
import fr.clementgre.pdf4teachers.utils.style.Style;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import fr.clementgre.pdf4teachers.utils.svg.SVGPathIcons;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class FooterBar extends StackPane {

    private final StackPane messagePane = new StackPane();
    private final Label message = new Label();

    private final HBox root = new HBox();

    private final HBox zoom = new HBox();
    private final SliderWithoutPopup zoomController = new SliderWithoutPopup(1, 20, 10);
    private final Label zoomPercent = new Label();
    private final ColorAdjust lightGrayColorAdjust = new ColorAdjust();
    private final ToggleGroup viewGroup = new ToggleGroup();
    private final ToggleButton columnView = new ToggleButton("", SVGPathIcons.generateImage(SVGPathIcons.SINGLE_PAGE, "white", 0, 25, lightGrayColorAdjust));
    private final ToggleButton gridView = new ToggleButton("", SVGPathIcons.generateImage(SVGPathIcons.MULTI_PAGE, "white", 0, 25, lightGrayColorAdjust));
    private final ToggleButton editPagesMode = new ToggleButton(TR.tr("footerBar.editPages"));
    private final HBox exerciseCorrection = new HBox();
    private final ToggleButton exerciseCorrectionMode = new ToggleButton(TR.tr("footerBar.exerciseMode"));
    private final ComboBox<String> exerciseSelector = new ComboBox<>();
    private final Button exercisePages = new Button(TR.tr("footerBar.exercisePages"));
    private final Label selectedElements = new Label();
    // Exercise pages of each evaluation, by evaluation signature (see ExercisePageMapping.getSignature).
    private final Map<String, ExercisePageMapping> exercisePageMappings = new LinkedHashMap<>();
    // Mapping loaded from the old config format (Q1, Q2... keys), migrated to the first evaluation opened.
    private ExercisePageMapping legacyExercisePageMapping;
    private List<String> exerciseKeys = List.of();
    private String selectedExerciseKey;

    private final Label statsElements = new Label();
    private final Label statsTexts = new Label();
    private final Label statsGrades = new Label();
    private final Label statsGraphics = new Label();
    private final Label statsTotalGrade = new Label();
    private final Label status = new Label();

    private final Region spacer = new Region();

    private int oldWidth;
    private final int widthLimit = 1350;
    private boolean updatingExerciseControls;

    public FooterBar(){
        StyleManager.putStyle(this, Style.ACCENT);
        getStyleClass().add("app-footer-bar");
        setMaxHeight(20);
        setMinHeight(20);
        setPadding(new Insets(0));
        setBorder(null);
        setup();
    }

    public void setup(){

        // ZOOM INFO
        zoomController.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.zoom")));
        zoomPercent.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.zoom")));
        zoom.setAlignment(Pos.CENTER_LEFT);
        zoomPercent.setMinWidth(40);
        zoom.setSpacing(5);

        zoomPercent.setText(((int) MainWindow.mainScreen.getZoomPercent()) + "%");
        MainWindow.mainScreen.pane.scaleXProperty().addListener((observable, oldValue, newValue) -> {
            zoomPercent.setText(((int) MainWindow.mainScreen.getZoomPercent()) + "%");
            if(zoomController.getValue() != newValue.doubleValue()){
                double scale = newValue.doubleValue();
                double val = 10;

                if(scale < 1){
                    val = scale * 10;
                }else if(scale > 1){
                    val = 10 + (10 * (scale - 1)) / 4;
                }
                zoomController.setValue(val);
            }
        });
        PaneUtils.setHBoxPosition(zoomController, 0, 20, 0);
        zoomController.valueProperty().addListener((observable, oldValue, newValue) -> {
            double val = newValue.doubleValue();
            double scale = 1;
            // val < 20 : scale = val / 20
            // val > 20 : scale = 1 + (4 * (val-20)) / 20
            if(val < 10){
                scale = val / 10;
            }else if(val > 10){
                scale = 1 + (4 * (val - 10)) / 10;
            }
            MainWindow.mainScreen.zoomOperator.zoom(scale, true);
        });

        columnView.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.columnView")));
        gridView.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.gridView")));
        columnView.setToggleGroup(viewGroup);
        gridView.setToggleGroup(viewGroup);
        PaneUtils.setHBoxPosition(columnView, -1, 19, new Insets(-2, 0, 0, 0));
        PaneUtils.setHBoxPosition(gridView, -1, 19, new Insets(-2, 5, 0, -5));
        ZoomOperator zoomOperator = MainWindow.mainScreen.zoomOperator;
        columnView.setOnAction(e -> zoomOperator.fitWidth(false, false));
        gridView.setOnAction(e -> zoomOperator.fitWidth(false, true));

        columnView.setSelected(true);

        viewGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null) viewGroup.selectToggle(oldValue);
            boolean gridView = viewGroup.getSelectedToggle() == this.gridView;
            if(MainWindow.mainScreen.isMultiPagesMode() != gridView) MainWindow.mainScreen.setIsMultiPagesMode(gridView);
        });
        MainWindow.mainScreen.isMultiPagesModeProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue && viewGroup.getSelectedToggle() != this.gridView) this.gridView.setSelected(true);
            if(!newValue && viewGroup.getSelectedToggle() != this.columnView) this.columnView.setSelected(true);
        });

        editPagesMode.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.editPages.tooltip")));
        PaneUtils.setHBoxPosition(editPagesMode, -1, 19, new Insets(-2, 0, 0, 0));
        MainWindow.mainScreen.isEditPagesModeProperty().bindBidirectional(editPagesMode.selectedProperty());

        columnView.disableProperty().bind(MainWindow.mainScreen.isEditPagesModeProperty().or(MainWindow.mainScreen.statusProperty().isNotEqualTo(MainScreen.Status.OPEN)));
        gridView.disableProperty().bind(MainWindow.mainScreen.isEditPagesModeProperty().or(MainWindow.mainScreen.statusProperty().isNotEqualTo(MainScreen.Status.OPEN)));
        
        setupExerciseCorrectionControls();

        zoom.getChildren().addAll(zoomPercent, zoomController, getSpacerShape(), editPagesMode, getSpacerShape(), columnView, gridView);

        HBox.setHgrow(spacer, Priority.ALWAYS);

        MainWindow.mainScreen.statusProperty().addListener((observable, oldValue, newValue) -> {
            updateStatus(newValue.intValue(), true);
            reloadGradingPanel();
        });
        updateStatus(MainScreen.Status.CLOSED, true);

        statsElements.setStyle("-fx-text-fill: #b2b2b2;");
        statsTexts.setStyle("-fx-text-fill: #b2b2b2;");
        statsGrades.setStyle("-fx-text-fill: #b2b2b2;");
        statsGraphics.setStyle("-fx-text-fill: #b2b2b2;");
        statsTotalGrade.setStyle("-fx-text-fill: #b2b2b2;");

        root.setPadding(new Insets(0, 10, 0, 10));
        root.setSpacing(10);
        root.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().setAll(zoom, spacer, getSpacerShape(), this.status);
        getChildren().add(root);

        messagePane.getChildren().add(message);
        messagePane.setTranslateY(20);
        messagePane.prefWidthProperty().bind(widthProperty());
        messagePane.setPrefHeight(20);
        message.prefWidthProperty().bind(widthProperty());

        widthProperty().addListener((observable, oldValue, newValue) -> {
            if(oldWidth > widthLimit && newValue.intValue() < widthLimit){
                updateStatus(MainWindow.mainScreen.getStatus(), true);
                oldWidth = newValue.intValue();
            }else if(oldWidth < widthLimit && newValue.intValue() > widthLimit){
                updateStatus(MainWindow.mainScreen.getStatus(), true);
                oldWidth = newValue.intValue();
            }
        });
    }
    public enum ToastDuration { SHORT(2000), MEDIUM(6000), LONG(10000);
        private final int duration;
        ToastDuration(int duration){
            this.duration = duration;
        }
        public int getDuration(){
            return duration;
        }
    }
    public void showToast(Color background, Color messageColor, String text){
        showToast(background, messageColor, ToastDuration.SHORT, text);
    }
    public void showToast(Color background, Color messageColor, ToastDuration duration, String text){
        if(!getChildren().contains(messagePane)){
            getChildren().add(messagePane);
            messagePane.setTranslateY(20);
        }

        messagePane.setBackground(new Background(new BackgroundFill(background, CornerRadii.EMPTY, Insets.EMPTY)));
        messagePane.setOpacity(0);
        message.setTextFill(messageColor);
        message.setText(text);
        message.setAlignment(Pos.CENTER);
        message.setStyle("-fx-font-weight: 800; -fx-font-family: Arial;");

        Platform.runLater(() -> {
            Timeline timelineShow = new Timeline(60);
            timelineShow.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(200), new KeyValue(messagePane.translateYProperty(), 0)),
                    new KeyFrame(Duration.millis(200), new KeyValue(messagePane.opacityProperty(), 1))
            );
            timelineShow.play();

            PlatformUtils.runLaterOnUIThread(duration.duration, () -> {
                Timeline timelineHide = new Timeline(60);
                timelineHide.getKeyFrames().addAll(
                        new KeyFrame(Duration.millis(200), new KeyValue(messagePane.translateYProperty(), 20)),
                        new KeyFrame(Duration.millis(200), new KeyValue(messagePane.opacityProperty(), 0))
                );
                timelineHide.play();
                timelineHide.setOnFinished((e) -> getChildren().remove(messagePane));
            });
        });
    }

    private Pane getSpacerShape(){
        Line shape = new Line(0, 0, 0, 14);
        shape.setStroke(Color.web("#4B4B4B"));
        shape.setStrokeWidth(1);

        StackPane pane = new StackPane();
        pane.getChildren().add(shape);
        pane.setPadding(new Insets(3));

        return pane;
    }

    public void updateCurrentPage(){
        updateStatus(MainWindow.mainScreen.getStatus(), false);
    }

    public void updateStatus(int status, boolean hard){
        if(status == MainScreen.Status.OPEN){
            if(hard){
                if(getWidth() < widthLimit){
                    root.getChildren().setAll(zoom, spacer, selectedElements, getSpacerShape(), this.status);
                }else{
                    root.getChildren().setAll(zoom, spacer, getSpacerShape(),
                            statsElements, getSpacerShape(), statsTexts, getSpacerShape(), statsGrades, getSpacerShape(), statsGraphics, getSpacerShape(), statsTotalGrade, getSpacerShape(),
                            selectedElements, getSpacerShape(), this.status);
                }

                updateStats();
                refreshExerciseChoices();
            }
            zoomController.setDisable(false);
            zoomPercent.setDisable(false);
            editPagesMode.setDisable(false);
            if(MainWindow.mainScreen.document.getLastCursorOverPage() == -1){
                this.status.setText(MainWindow.mainScreen.document.getFileName() + " - " + "?/" + MainWindow.mainScreen.document.numberOfPages);
            }else
                this.status.setText(MainWindow.mainScreen.document.getFileName() + " - " + (MainWindow.mainScreen.document.getLastCursorOverPage() + 1) + "/" + MainWindow.mainScreen.document.numberOfPages);

        }else{
            zoomController.setDisable(true);
            zoomPercent.setDisable(true);
            editPagesMode.setDisable(true);
            if(hard){
                root.getChildren().setAll(zoom, spacer, getSpacerShape(), this.status);
            }

            if(status == MainScreen.Status.CLOSED){
                this.status.setText(TR.tr("footerBar.documentStatus.noDocument"));
            }else if(status == MainScreen.Status.ERROR || status == MainScreen.Status.ERROR_EDITION){
                this.status.setText(TR.tr("footerBar.documentStatus.error"));
            }
        }
    }

    public void updateStats(){
        if(MainWindow.mainScreen.hasDocument(false)){
            Platform.runLater(() -> {
                if(MainWindow.mainScreen.document == null) return;
                int[] count = MainWindow.mainScreen.document.countElements();
                statsElements.setText(count[0] + " " + TR.tr("elements.name"));
                statsTexts.setText(count[1] + " " + TR.tr("elements.name.texts"));
                statsGrades.setText(count[2] + " " + TR.tr("elements.name.grades"));
                statsGraphics.setText(count[3] + " " + TR.tr("elements.name.paints"));

                if(GradeTreeView.getTotal() != null){
                    double grade = GradeTreeView.getTotal().getCore().getVisibleValue();
                    double total = GradeTreeView.getTotal().getCore().getVisibleTotal();

                    if(GradeTreeView.getTotal().getCore().getOutOfTotal() >= 0 && total != 0){
                        grade = grade * GradeTreeView.getTotal().getCore().getOutOfTotal() / total;
                        total = GradeTreeView.getTotal().getCore().getOutOfTotal();
                    }

                    statsTotalGrade.setText(MainWindow.twoDigFormat.format(grade) + "/" + MainWindow.twoDigFormat.format(total));
                }
            });
        }
    }
    
    private void setupExerciseCorrectionControls(){
        exerciseCorrection.setAlignment(Pos.CENTER_LEFT);
        exerciseCorrection.setSpacing(5);
        
        exerciseCorrectionMode.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.exerciseMode.tooltip")));
        PaneUtils.setHBoxPosition(exerciseCorrectionMode, -1, 19, new Insets(-2, 0, 0, 0));
        exerciseCorrectionMode.setOnAction(e -> {
            ExerciseCorrectionData.requestSave();
            ScoredComments.fireChanged(false);
            if(exerciseCorrectionMode.isSelected()){
                refreshExerciseChoices();
                MainWindow.filesTab.preloadNeighborExercisePages();
                navigateToSelectedExercisePage();
            }
        });
        
        exerciseSelector.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.exerciseSelector.tooltip")));
        exerciseSelector.setPrefWidth(130); // Shows the exercise names
        exerciseSelector.setMaxHeight(19);
        exerciseSelector.setOnAction(e -> {
            ScoredComments.fireChanged(false); // The scored comments panel lists the entries of the selected exercise
            if(updatingExerciseControls) return;
            String selected = exerciseSelector.getSelectionModel().getSelectedItem();
            if(selected != null) selectedExerciseKey = selected;
            ExerciseCorrectionData.requestSave();
            MainWindow.filesTab.preloadNeighborExercisePages();
            navigateToSelectedExercisePage();
            reloadGradingPanel();
        });
        
        exercisePages.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("footerBar.exercisePages.tooltip")));
        PaneUtils.setHBoxPosition(exercisePages, -1, 19, new Insets(-2, 0, 0, 0));
        exercisePages.setOnAction(e -> editExercisePages());
        
        exerciseCorrectionMode.disableProperty().bind(MainWindow.mainScreen.statusProperty().isNotEqualTo(MainScreen.Status.OPEN));
        exerciseSelector.disableProperty().bind(exerciseCorrectionMode.disableProperty());
        exercisePages.disableProperty().bind(exerciseCorrectionMode.disableProperty());
        
        selectedElements.setStyle("-fx-text-fill: #b2b2b2;");
        selectedElements.visibleProperty().bind(MainWindow.mainScreen.selectedElementsCountProperty().greaterThan(1));
        selectedElements.managedProperty().bind(selectedElements.visibleProperty());
        MainWindow.mainScreen.selectedElementsCountProperty().addListener((observable, oldValue, newValue) -> {
            selectedElements.setText(TR.tr("footerBar.selectedElements", newValue.intValue()));
        });
        selectedElements.setText("");
        
        exerciseCorrection.getChildren().addAll(exerciseCorrectionMode, exerciseSelector, exercisePages);
        refreshExerciseChoices();
    }
    
    // The page of each exercise, set by hand (by default: the page where its sub-grades are). Returns true if changed.
    public boolean editExercisePages(){
        if(!MainWindow.mainScreen.hasDocument(false)) return false;
        refreshExerciseChoices();
        List<String> exerciseKeys = getExerciseKeys();
        if(exerciseKeys.isEmpty()){
            showToast(Color.web("#6a1b1b"), Color.WHITE, TR.tr("footerBar.exercisePages.noGrades"));
            return false;
        }
        boolean applied = new ExercisePageMappingDialog(getExercisePageMapping(), exerciseKeys, MainWindow.mainScreen.document.getPagesNumber()).show();
        if(!applied) return false;
        ExerciseCorrectionData.requestSave();
        MainWindow.filesTab.preloadNeighborExercisePages();
        navigateToSelectedExercisePage();
        reloadGradingPanel();
        return true;
    }
    
    private List<String> getExerciseKeys(){
        return exerciseKeys;
    }
    
    public void refreshExerciseChoices(){
        if(MainWindow.gradeTab == null || MainWindow.gradeTab.treeView == null) return;
        
        updatingExerciseControls = true;
        exerciseKeys = ExercisePageMapping.buildExerciseKeys(getExerciseNamesFromGradeScale());
        exerciseSelector.getItems().setAll(exerciseKeys);
        if(exerciseKeys.contains(selectedExerciseKey)){
            exerciseSelector.getSelectionModel().select(selectedExerciseKey);
        }else if(!exerciseKeys.isEmpty()){
            exerciseSelector.getSelectionModel().selectFirst();
            selectedExerciseKey = exerciseSelector.getSelectionModel().getSelectedItem();
        }
        updatingExerciseControls = false;
        reloadGradingPanel();
    }
    
    public void reloadGradingPanel(){
        if(MainWindow.gradingPanel != null) MainWindow.gradingPanel.reload();
        // The texts tab opens the comments of the selected exercise
        if(MainWindow.textTab != null) MainWindow.textTab.treeView.evaluationSection.onExerciseChanged();
    }
    
    private List<String> getExerciseNamesFromGradeScale(){
        if(GradeTreeView.getTotal() == null) return List.of();
        return GradeTreeView.getTotal().getChildren().stream()
                .map(item -> ((GradeTreeItem) item).getCore().getName())
                .toList();
    }
    public int getExerciseCount(){
        return exerciseKeys.size();
    }
    // Key of the exercise at this index in the grade scale, or null.
    public String getExerciseKey(int topLevelIndex){
        if(topLevelIndex < 0 || topLevelIndex >= exerciseKeys.size()) return null;
        return exerciseKeys.get(topLevelIndex);
    }
    public int getSelectedExerciseIndex(){
        return exerciseKeys.indexOf(selectedExerciseKey);
    }
    
    // Grading by exercise: while the grading panel is open, the other copies open at the page of its exercise
    // (Modified by Nathan, 2026: the exercise controls moved from this bar to the grading panel)
    public boolean isExerciseCorrectionMode(){
        return MainWindow.gradingTab != null && MainWindow.gradingTab.isSelected();
    }
    
    // Page set for the selected exercise, or else the page of its first sub-grade in the open document.
    public OptionalInt getSelectedExercisePageIndex(){
        return getExercisePage(getSelectedExerciseIndex());
    }
    public OptionalInt getSelectedExerciseGradesPage(){
        return getExerciseGradesPage(getSelectedExercise());
    }
    // Page set for the exercise at this index, or else the page of its first sub-grade in the open document.
    public OptionalInt getExercisePage(int topLevelIndex){
        String key = getExerciseKey(topLevelIndex);
        if(key == null) return OptionalInt.empty();
        OptionalInt mapped = getExercisePageMapping().getPageIndex(key);
        if(mapped.isPresent()) return mapped;
        return getExerciseGradesPage(getExercise(topLevelIndex));
    }
    private static OptionalInt getExerciseGradesPage(GradeTreeItem exercise){
        if(exercise == null) return OptionalInt.empty();
        return GradeTreeView.getGradesArray(exercise).stream()
                .filter(item -> !item.hasSubGrade())
                .mapToInt(item -> item.getCore().getPageNumber())
                .min();
    }
    // The pages of the exercises cannot be told: none is set, and the grades of all the exercises are on the same page
    // (e.g. in a table on the first page) of a copy that has several pages.
    public boolean areExercisePagesUnknown(){
        if(!MainWindow.mainScreen.hasDocument(false) || MainWindow.mainScreen.document.getPagesNumber() < 2 || exerciseKeys.size() < 2) return false;
        java.util.Set<Integer> pages = new java.util.HashSet<>();
        for(int i = 0; i < exerciseKeys.size(); i++){
            if(getExercisePageMapping().getPageIndex(exerciseKeys.get(i)).isPresent()) return false;
            OptionalInt page = getExerciseGradesPage(getExercise(i));
            if(page.isPresent()) pages.add(page.getAsInt());
        }
        return pages.size() <= 1;
    }
    
    public GradeTreeItem getSelectedExercise(){
        return getExercise(getSelectedExerciseIndex());
    }
    private static GradeTreeItem getExercise(int topLevelIndex){
        if(GradeTreeView.getTotal() == null || topLevelIndex < 0 || topLevelIndex >= GradeTreeView.getTotal().getChildren().size()) return null;
        return (GradeTreeItem) GradeTreeView.getTotal().getChildren().get(topLevelIndex);
    }
    // Mapping of the evaluation currently open.
    public ExercisePageMapping getExercisePageMapping(){
        return exercisePageMappings.computeIfAbsent(ExercisePageMapping.getSignature(exerciseKeys), signature -> {
            if(exerciseKeys.isEmpty()) return new ExercisePageMapping();
            // The grade scale changed a bit (exercise added or renamed): keep the pages of the exercises that still exist.
            ExercisePageMapping closest = ExercisePageMapping.findClosest(exercisePageMappings.values(), exerciseKeys);
            if(closest != null){
                ExerciseCorrectionData.requestSave();
                return closest.copyFor(exerciseKeys);
            }
            if(legacyExercisePageMapping == null) return new ExercisePageMapping();
            ExercisePageMapping migrated = ExercisePageMapping.fromLegacyQuestionKeys(legacyExercisePageMapping, exerciseKeys);
            legacyExercisePageMapping = null;
            ExerciseCorrectionData.requestSave();
            return migrated;
        });
    }
    public Map<String, ExercisePageMapping> getExercisePageMappings(){
        return exercisePageMappings;
    }
    public ExercisePageMapping getLegacyExercisePageMapping(){
        return legacyExercisePageMapping;
    }
    public void setLegacyExercisePageMapping(ExercisePageMapping legacyExercisePageMapping){
        this.legacyExercisePageMapping = legacyExercisePageMapping;
    }
    public void setExerciseCorrectionMode(boolean enabled){
        exerciseCorrectionMode.setSelected(enabled);
    }
    public String getSelectedExerciseKey(){
        return selectedExerciseKey;
    }
    public void setSelectedExerciseKey(String selectedExerciseKey){
        if(selectedExerciseKey == null || selectedExerciseKey.isBlank()) return;
        
        this.selectedExerciseKey = selectedExerciseKey;
        if(exerciseSelector.getItems().contains(selectedExerciseKey)){
            updatingExerciseControls = true;
            exerciseSelector.getSelectionModel().select(selectedExerciseKey);
            updatingExerciseControls = false;
            reloadGradingPanel();
        }
    }
    public OptionalInt getExercisePageIndex(String exerciseKey){
        if(exerciseKey == null) return OptionalInt.empty();
        return getExercisePageMapping().getPageIndex(exerciseKey);
    }
    
    // Returns false if exercise correction mode is off, so that the key event can be used by something else.
    public boolean selectNeighborExercise(int delta){
        if(!isExerciseCorrectionMode() && (MainWindow.gradingTab == null || !MainWindow.gradingTab.isSelected())) return false;
        return selectExercise(delta);
    }
    // Selects the previous (delta = -1) or next (delta = 1) exercise, also outside exercise correction mode.
    public boolean selectExercise(int delta){
        if(!MainWindow.mainScreen.hasDocument(false) || exerciseSelector.getItems().isEmpty()) return false;
        int index = Math.clamp(exerciseSelector.getSelectionModel().getSelectedIndex() + delta, 0, exerciseSelector.getItems().size() - 1);
        exerciseSelector.getSelectionModel().select(index); // Fires the selector action: saves and navigates.
        showToast(Color.web("#424242"), Color.WHITE, exerciseSelector.getSelectionModel().getSelectedItem());
        return true;
    }
    
    // Selects the exercise at this index and scrolls to its page, even if it is already selected.
    public boolean goToExercise(int topLevelIndex){
        if(!MainWindow.mainScreen.hasDocument(false) || topLevelIndex < 0 || topLevelIndex >= exerciseSelector.getItems().size()) return false;
        if(topLevelIndex == exerciseSelector.getSelectionModel().getSelectedIndex()) navigateToSelectedExercisePage();
        else exerciseSelector.getSelectionModel().select(topLevelIndex); // Fires the selector action: saves and navigates.
        return true;
    }
    
    public void navigateToSelectedExercisePage(){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        OptionalInt pageIndex = ExerciseCorrectionWorkflow.getNavigationTarget(true, getSelectedExercisePageIndex(), MainWindow.mainScreen.document.getPagesNumber());
        if(pageIndex.isEmpty()) return;
        int targetPageIndex = pageIndex.getAsInt();
        Platform.runLater(() -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            MainWindow.mainScreen.document.prefetchPages(targetPageIndex, ExerciseCorrectionWorkflow.getPrefetchLastPage(targetPageIndex, MainWindow.mainScreen.document.getPagesNumber()));
            MainWindow.mainScreen.zoomOperator.scrollToPage(MainWindow.mainScreen.document.getPage(targetPageIndex));
        });
    }

    public Node getEditPagesModeNode(){
        return editPagesMode;
    }
    public Node getViewModeNode(){
        return gridView;
    }
}
