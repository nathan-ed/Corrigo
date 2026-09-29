/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.TextTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.TreeViewSections.TextTreeSection;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.*;

/**
 * "This evaluation" section of the texts tab: the comments written on the copies of the evaluation (EvaluationComments),
 * in one group per exercise, the most used first. The group of the exercise being corrected is opened.
 */
public class EvaluationCommentsSection extends TextTreeSection {

    // Exercise names whose group is open (null key: general)
    private final Set<String> expanded = new HashSet<>();
    // Groups opened by the teacher, which are not closed when the exercise changes
    private final Set<String> openedByTeacher = new HashSet<>();
    private String lastSelectedExercise;
    private boolean updateScheduled;

    public EvaluationCommentsSection(){
        super(TR.tr("textTab.evaluationList.name"), EVAL_TYPE);
        setupGraphics();
        // The comments are sorted by use in each exercise
        sortToggleBtn.setVisible(false);
        sortToggleBtn.setManaged(false);
        menu = buildSectionMenu();

        EvaluationComments.revisionProperty().addListener((o, oldValue, newValue) -> scheduleUpdate());
    }

    @Override
    public void setupSortManager(){
        sortManager.setup(sortCell.pane, TR.tr("sorting.sortType.use"), TR.tr("sorting.sortType.use"));
    }

    private ContextMenu buildSectionMenu(){
        MenuItem rescan = new MenuItem(TR.tr("textTab.evaluationList.rescan"));
        rescan.setOnAction(e -> EvaluationComments.rescanAll());
        return new ContextMenu(rescan);
    }

    public void scheduleUpdate(){
        if(updateScheduled) return;
        updateScheduled = true;
        Platform.runLater(() -> {
            updateScheduled = false;
            update();
        });
    }

    // The exercise being corrected changed: its group is opened.
    public void onExerciseChanged(){
        String selected = getSelectedExercise();
        if(Objects.equals(selected, lastSelectedExercise)) return;
        // The group opened for the previous exercise is closed (the groups opened by the teacher stay open)
        if(lastSelectedExercise != null && !openedByTeacher.contains(lastSelectedExercise)) expanded.remove(lastSelectedExercise);
        lastSelectedExercise = selected;
        if(selected != null) expanded.add(selected);
        scheduleUpdate();
    }
    public void expand(String exercise){
        expanded.add(exercise);
        openedByTeacher.add(exercise);
        scheduleUpdate();
    }

    private static String getSelectedExercise(){
        if(MainWindow.footerBar == null) return null;
        GradeTreeItem exercise = MainWindow.footerBar.getSelectedExercise();
        return exercise == null ? null : exercise.getCore().getName();
    }

    private void update(){
        // Remembers the groups opened or closed by the teacher
        for(TreeItem<String> child : getChildren()){
            if(child instanceof ExerciseGroupItem group){
                boolean wasExpanded = expanded.contains(group.getExercise());
                if(group.isExpanded() && !wasExpanded) openedByTeacher.add(group.getExercise());
                if(!group.isExpanded()) openedByTeacher.remove(group.getExercise());
                if(group.isExpanded()) expanded.add(group.getExercise());
                else expanded.remove(group.getExercise());
            }
        }
        getChildren().removeIf(child -> child instanceof ExerciseGroupItem);

        LinkedHashMap<String, List<CommentBank.Entry>> groups = new LinkedHashMap<>();
        for(String exercise : EvaluationComments.ExerciseContext.current().order()) groups.put(exercise, new ArrayList<>());
        List<CommentBank.Entry> general = new ArrayList<>();
        for(CommentBank.Entry entry : EvaluationComments.getBank().getVisibleEntries()){
            if(entry.getExercise() == null) general.add(entry);
            else groups.computeIfAbsent(entry.getExercise(), k -> new ArrayList<>()).add(entry);
        }
        groups.put(null, general);

        String selected = getSelectedExercise();
        Comparator<CommentBank.Entry> byUse = Comparator.comparingInt(CommentBank.Entry::getUses).reversed()
                .thenComparing(CommentBank.Entry::getText, String.CASE_INSENSITIVE_ORDER);
        for(Map.Entry<String, List<CommentBank.Entry>> group : groups.entrySet()){
            if(group.getValue().isEmpty()) continue;
            ExerciseGroupItem item = new ExerciseGroupItem(group.getKey(), group.getValue().size(), Objects.equals(group.getKey(), selected));
            for(CommentBank.Entry entry : group.getValue().stream().sorted(byUse).toList()) item.getChildren().add(new EvaluationCommentItem(entry));
            item.setExpanded(expanded.contains(group.getKey()));
            getChildren().add(item);
        }
    }

    // All the comments, in all the groups.
    @Override
    public List<TextTreeItem> getTextItems(){
        ArrayList<TextTreeItem> items = new ArrayList<>();
        for(TreeItem<String> child : getChildren()){
            if(child instanceof ExerciseGroupItem group){
                for(TreeItem<String> item : group.getChildren()) if(item instanceof TextTreeItem textItem) items.add(textItem);
            }
        }
        return items;
    }
    @Override
    public void updateChildrenGraphics(){
        for(TextTreeItem item : getTextItems()) item.updateGraphic(true);
    }

    // Group of the comments of an exercise
    public static class ExerciseGroupItem extends TreeItem<String> {
        private final String exercise;
        private final HBox pane = new HBox(6);

        ExerciseGroupItem(String exercise, int count, boolean current){
            this.exercise = exercise;
            Text name = new Text(exercise == null ? TR.tr("textTab.evaluationList.general") : exercise);
            name.setFont(Font.font(null, current ? FontWeight.BOLD : FontWeight.NORMAL, 13));
            name.setFill(StyleManager.shiftColorWithTheme(javafx.scene.paint.Color.BLACK));
            Text size = new Text(String.valueOf(count));
            size.setFont(Font.font(11));
            size.setFill(javafx.scene.paint.Color.GRAY);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            pane.getChildren().addAll(name, spacer, size);
            pane.setAlignment(Pos.CENTER_LEFT);
            if(current) pane.setStyle("-fx-border-color: " + StyleManager.getHexAccentColor() + "; -fx-border-width: 0 0 0 3; -fx-padding: 0 0 0 4;");
        }
        public String getExercise(){
            return exercise;
        }
        private static boolean isIn(Object target, javafx.scene.Node parent){
            for(javafx.scene.Node node = target instanceof javafx.scene.Node n ? n : null; node != null; node = node.getParent()){
                if(node == parent) return true;
            }
            return false;
        }
        public void updateCell(TreeCell<String> cell){
            cell.setGraphic(pane);
            cell.setStyle("-fx-padding: 3 6 3 0;");
            cell.setContextMenu(null);
            // A click on the name opens or closes the group (the arrow already does it)
            cell.setOnMouseClicked(e -> {
                if(e.getClickCount() == 1 && !isIn(e.getTarget(), cell.getDisclosureNode())) setExpanded(!isExpanded());
            });
            cell.setMinHeight(Region.USE_COMPUTED_SIZE);
            cell.setPrefHeight(Region.USE_COMPUTED_SIZE);
            cell.setMaxHeight(Region.USE_COMPUTED_SIZE);
        }
    }
}
