/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.datasaving.evaluation.EvaluationFolders;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation.CommentBank;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation.CommentUsages;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation.CommentUsagesWindow;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.File;
import java.util.*;

/**
 * "Methods & mistakes" of the exercise being graded, in the grading panel: a chip per tag, those of the exercise first
 * then the others (a click adds it to the copy for this exercise or removes it, a click on its number shows the copies
 * that have it in this exercise), the tags creation, and an overview of the class, for this exercise (copies, share
 * and average points by tag, and the copies without method) or for the whole evaluation (copies and exercises by tag).
 */
public class ExerciseTagsCard extends VBox {

    // Colors of the grading panel
    public record Colors(String card, String border, String text, String muted, String method, String mistake) {}

    private final Colors colors;
    private GradeTreeItem exercise;
    private Map<String, Double> scores = Map.of();
    private String scoresExercise;
    private boolean overviewOpen;
    private boolean overviewAll; // Overview of the whole evaluation instead of the exercise
    private final PauseTransition scoresDelay = new PauseTransition(Duration.millis(300));

    public ExerciseTagsCard(Colors colors){
        super(8);
        this.colors = colors;
        setPadding(new Insets(10, 10, 10, 10));
        setStyle("-fx-background-color: " + colors.card() + "; -fx-border-color: " + colors.border() + "; -fx-border-radius: 6; -fx-background-radius: 6;");
        ExerciseTags.revisionProperty().addListener((o, oldValue, newValue) -> update());
        scoresDelay.setOnFinished(e -> loadScores());
    }

    public void setExercise(GradeTreeItem exercise){
        boolean changed = this.exercise == null || exercise == null || this.exercise.getCore() != exercise.getCore();
        this.exercise = exercise;
        if(changed) scores = Map.of();
        update();
    }

    // The points may have changed: read again a bit later
    public void refreshScores(){
        if(overviewOpen) scoresDelay.playFromStart();
    }

    private String getExerciseName(){
        return exercise == null ? null : exercise.getCore().getName();
    }
    private String getColor(Kind kind){
        return kind == Kind.METHOD ? colors.method() : colors.mistake();
    }

    public void update(){
        getChildren().clear();
        String exerciseName = getExerciseName();
        if(exerciseName == null || EvaluationFolders.getActiveFolder() == null) return;
        EvaluationTags data = ExerciseTags.getData();
        String copy = ExerciseTags.getOpenCopy();
        List<Tag> tags = data.getTags(exerciseName);

        // Title
        Label title = new Label(TR.tr("tags.card.title"));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: " + colors.text() + ";");
        Label shortcut = new Label("#");
        shortcut.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + "; -fx-border-color: " + colors.border() + "; -fx-border-radius: 3; -fx-padding: 0 4;");
        shortcut.setTooltip(new Tooltip(TR.tr("tags.card.shortcut")));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, title, spacer, shortcut);
        header.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(header);

        int copies = ExerciseTags.getFolderCopies().size();
        List<Tag> ordered = getOrderedTags(exerciseName);
        for(Kind kind : Kind.values()){
            FlowPane chips = new FlowPane(5, 5);
            for(Tag tag : ordered){
                if(tag.getKind() != kind) continue;
                int index = ordered.indexOf(tag);
                chips.getChildren().add(buildChip(tag, copy == null ? 0 : data.count(copy, exerciseName, tag),
                        data.getCopies(exerciseName, tag).size(), copies, index < 9 ? index + 1 : 0));
            }
            chips.getChildren().add(buildAdd(kind));
            Label kindLabel = new Label(TR.tr(kind == Kind.METHOD ? "tags.kind.methods" : "tags.kind.mistakes"));
            kindLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            getChildren().add(new VBox(3, kindLabel, chips));
        }

        // Class
        List<String> allCopies = ExerciseTags.getFolderCopies().stream().map(File::getName).toList();
        List<String> withoutMethod = data.getWithoutMethod(exerciseName, allCopies);
        boolean hasMethods = data.getExerciseTags(exerciseName).stream().anyMatch(tag -> tag.getKind() == Kind.METHOD);
        if(hasMethods && !allCopies.isEmpty()){
            Label summary = new Label(TR.tr("tags.card.classified", String.valueOf(allCopies.size() - withoutMethod.size()), String.valueOf(allCopies.size())));
            summary.setWrapText(true);
            summary.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            VBox summaryBox = new VBox(2, summary);
            if(!withoutMethod.isEmpty()){
                Hyperlink show = link(TR.tr("tags.card.showUnclassified", String.valueOf(withoutMethod.size())));
                show.setOnAction(e -> showWithoutMethod(withoutMethod));
                Hyperlink review = link(TR.tr("tags.card.review"));
                review.setTooltip(new Tooltip(TR.tr("tags.card.review.tooltip")));
                review.setOnAction(e -> TagReview.start(new TagReview.Review(TR.tr("tags.review.unclassified", exerciseName), withoutMethod, Map.of())));
                FlowPane links = new FlowPane(10, 2, show, review);
                summaryBox.getChildren().add(links);
            }
            getChildren().add(summaryBox);
        }

        // Overview
        if(!data.isEmpty()){
            Hyperlink toggle = link(TR.tr(overviewOpen ? "tags.card.overview.hide" : "tags.card.overview.show"));
            toggle.setOnAction(e -> {
                overviewOpen = !overviewOpen;
                if(overviewOpen) loadScores();
                update();
            });
            getChildren().add(toggle);
            if(overviewOpen){
                getChildren().add(buildScopes());
                getChildren().add(overviewAll ? buildGlobalOverview(allCopies.size()) : buildOverview(data.getExerciseTags(exerciseName), allCopies.size()));
            }
        }

        // Pills on the pages of the copy (not printed)
        CheckBox markers = new CheckBox(TR.tr("tags.card.markers"));
        markers.setSelected(TagMarkers.isShown());
        markers.setFocusTraversable(false);
        markers.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
        markers.setTooltip(new Tooltip(TR.tr("tags.card.markers.tooltip")));
        markers.setOnAction(e -> TagMarkers.setShown(markers.isSelected()));
        getChildren().add(markers);
    }

    private Hyperlink link(String text){
        Hyperlink link = new Hyperlink(text);
        link.setFocusTraversable(false);
        link.setPadding(Insets.EMPTY);
        link.setStyle("-fx-font-size: 11;");
        return link;
    }

    // CHIPS

    // The tags of the exercise as the card shows them: the methods, then the mistakes (keys 1 to 9 of the grading panel)
    public static List<Tag> getOrderedTags(String exerciseName){
        List<Tag> tags = ExerciseTags.getData().getTags(exerciseName);
        ArrayList<Tag> ordered = new ArrayList<>();
        for(Kind kind : Kind.values()) tags.stream().filter(tag -> tag.getKind() == kind).forEach(ordered::add);
        return ordered;
    }

    // Adds an occurrence of the tag on the open copy, for this exercise (from the panel: not at a spot of the copy)
    public static void addToOpenCopy(String exerciseName, Tag tag){
        String copy = ExerciseTags.getOpenCopy();
        if(copy != null) ExerciseTags.addOccurrence(copy, exerciseName, tag, ExerciseTags.getExercisePlacement());
    }
    // Removes the last occurrence of the tag on the open copy for this exercise. Returns false if there is none.
    public static boolean removeLastFromOpenCopy(String exerciseName, Tag tag){
        String copy = ExerciseTags.getOpenCopy();
        if(copy == null) return false;
        List<EvaluationTags.Use> uses = ExerciseTags.getData().getUses(copy, exerciseName, tag);
        if(uses.isEmpty()) return false;
        ExerciseTags.removeOccurrence(copy, uses.getLast().id());
        return true;
    }

    // occurrences: on the open copy for this exercise; count: copies having it in this exercise; key: 1-9 or 0
    private Node buildChip(Tag tag, int occurrences, int count, int copies, int key){
        String color = getColor(tag.getKind());
        boolean applied = occurrences > 0;
        Label keyLabel = new Label(key == 0 ? "" : String.valueOf(key));
        keyLabel.setManaged(key != 0);
        Label name = new Label(tag.getName() + (occurrences > 1 ? " ×" + occurrences : ""));
        Label points = new Label(tag.getPointsLabel(getExerciseName(), MainWindow.gradesDigFormat));
        points.setManaged(!points.getText().isEmpty());
        Label badge = new Label(String.valueOf(count));
        HBox chip = new HBox(5, keyLabel, name, points);
        if(count > 0) chip.getChildren().add(badge);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setCursor(Cursor.HAND);
        chip.setPadding(new Insets(3, count == 0 ? 10 : 5, 3, key == 0 ? 10 : 7));
        String text = applied ? "white" : color;
        if(applied){
            chip.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 14; -fx-border-color: " + color + "; -fx-border-radius: 14;");
            name.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
            badge.setStyle("-fx-text-fill: white; -fx-background-color: rgba(255,255,255,.28); -fx-background-radius: 9; -fx-padding: 0 6; -fx-font-size: 11;");
        }else{
            chip.setStyle("-fx-background-color: transparent; -fx-border-color: " + color + "; -fx-border-radius: 14; -fx-background-radius: 14;");
            name.setStyle("-fx-text-fill: " + color + ";");
            badge.setStyle("-fx-text-fill: " + color + "; -fx-background-color: " + color + "22; -fx-background-radius: 9; -fx-padding: 0 6; -fx-font-size: 11;");
        }
        keyLabel.setStyle("-fx-text-fill: " + text + "; -fx-opacity: .6; -fx-font-size: 10; -fx-font-weight: bold;");
        points.setStyle("-fx-text-fill: " + text + "; -fx-font-size: 11; -fx-font-weight: bold;");
        badge.setCursor(Cursor.HAND);
        Tooltip.install(badge, new Tooltip(TR.tr("tags.chip.showCopies")));
        String share = copies == 0 ? "" : " (" + Math.round(100.0 * count / copies) + " %)";
        StringBuilder tooltip = new StringBuilder(TR.tr(applied ? "tags.chip.remove" : "tags.chip.add")).append("\n").append(TR.tr("tags.chip.more"));
        if(key != 0) tooltip.append("\n").append(TR.tr("tags.chip.key", String.valueOf(key)));
        String written = TagEditFiles.describe(tag, getExerciseName(), MainWindow.gradesDigFormat);
        if(!written.isEmpty()) tooltip.append("\n").append(TR.tr("tags.chip.written", written));
        tooltip.append("\n").append(TR.tr("tags.chip.count", String.valueOf(count))).append(share);
        Tooltip.install(name, new Tooltip(tooltip.toString()));
        chip.setOnMouseClicked(e -> {
            if(e.getButton() != MouseButton.PRIMARY) return;
            String copy = ExerciseTags.getOpenCopy();
            if(isIn(e.getTarget(), badge)) showCopies(tag, false);
            else if(copy == null) return;
            else if(e.isShiftDown() || !applied) addToOpenCopy(getExerciseName(), tag); // Shift: one more occurrence
            else ExerciseTags.removeAllOccurrences(copy, getExerciseName(), tag);
        });
        chip.setOnContextMenuRequested(e -> {
            buildMenu(tag).show(chip, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return chip;
    }
    private static boolean isIn(Object target, Node parent){
        for(Node node = target instanceof Node n ? n : null; node != null; node = node.getParent()) if(node == parent) return true;
        return false;
    }

    private ContextMenu buildMenu(Tag tag){
        EvaluationTags data = ExerciseTags.getData();
        MenuItem show = new MenuItem(TR.tr("tags.menu.showCopies"));
        show.setOnAction(e -> showCopies(tag, false));
        MenuItem review = new MenuItem(TR.tr("tags.menu.review"));
        review.setOnAction(e -> startReview(tag, false));
        review.setDisable(data.getCopies(getExerciseName(), tag).isEmpty());
        MenuItem showAll = new MenuItem(TR.tr("tags.menu.showCopiesAll"));
        showAll.setOnAction(e -> showCopies(tag, true));
        MenuItem reviewAll = new MenuItem(TR.tr("tags.menu.reviewAll"));
        reviewAll.setOnAction(e -> startReview(tag, true));
        reviewAll.setDisable(data.getCopies(tag).isEmpty());
        MenuItem scoring = new MenuItem(TR.tr("tags.menu.scoring"));
        scoring.setOnAction(e -> TagScoringDialog.show(tag, getExerciseName(), getScene() == null ? null : getScene().getWindow()));
        MenuItem rename = new MenuItem(TR.tr("tags.menu.rename"));
        rename.setOnAction(e -> rename(tag));
        MenuItem kind = new MenuItem(TR.tr(tag.getKind() == Kind.METHOD ? "tags.menu.toMistake" : "tags.menu.toMethod"));
        kind.setOnAction(e -> ExerciseTags.setKind(tag, tag.getKind() == Kind.METHOD ? Kind.MISTAKE : Kind.METHOD));
        MenuItem delete = new MenuItem(TR.tr("tags.menu.delete"));
        delete.setOnAction(e -> delete(tag));
        return new ContextMenu(scoring, new SeparatorMenuItem(), show, review, showAll, reviewAll, new SeparatorMenuItem(), rename, kind,
                new SeparatorMenuItem(), delete);
    }

    // "+ Method": a name field and a points field (optional), Enter creates the tag and adds it to the copy
    private Node buildAdd(Kind kind){
        Label add = new Label(TR.tr(kind == Kind.METHOD ? "tags.add.method" : "tags.add.mistake"));
        add.setCursor(Cursor.HAND);
        add.setPadding(new Insets(3, 10, 3, 10));
        add.setStyle("-fx-text-fill: " + colors.muted() + "; -fx-border-color: " + colors.border() + "; -fx-border-style: dashed; -fx-border-radius: 14;");
        add.setOnMouseClicked(e -> {
            if(e.getButton() != MouseButton.PRIMARY) return;
            TextField field = new TextField();
            field.setPromptText(TR.tr(kind == Kind.METHOD ? "tags.add.method.prompt" : "tags.add.mistake.prompt"));
            field.setPrefColumnCount(11);
            TextField pointsField = new TextField();
            pointsField.setPromptText(TR.tr(kind == Kind.METHOD ? "tags.add.points.method" : "tags.add.points.mistake"));
            pointsField.setPrefColumnCount(4);
            pointsField.setTooltip(new Tooltip(TR.tr(kind == Kind.METHOD ? "tags.scoring.points.method" : "tags.scoring.points.mistake")));
            TextField commentField = new TextField();
            commentField.setPromptText(TR.tr("tags.picker.comment"));
            commentField.setTooltip(new Tooltip(TR.tr("tags.scoring.comment.prompt")));
            HBox.setHgrow(commentField, Priority.ALWAYS);
            field.setMaxWidth(Double.MAX_VALUE);
            HBox second = new HBox(4, pointsField, commentField);
            second.setAlignment(Pos.CENTER_LEFT);
            VBox fields = new VBox(4, field, second);
            fields.setPrefWidth(230);
            Runnable create = () -> {
                EvaluationTags.Typed typed = EvaluationTags.parseTyped(field.getText());
                if(typed.name().isEmpty()){
                    update();
                    return;
                }
                Double points = parsePoints(pointsField.getText());
                if(points == null) points = typed.points(); // "Sign error -1" in the name field
                boolean isNew = ExerciseTags.getData().find(typed.name()).isEmpty();
                Tag tag = ExerciseTags.getData().create(getExerciseName(), typed.name(), kind);
                if(isNew && (points != null || !commentField.getText().isBlank())) ExerciseTags.getData().setScoring(tag, points, commentField.getText());
                String copy = ExerciseTags.getOpenCopy();
                if(copy != null && !ExerciseTags.getData().has(copy, getExerciseName(), tag)) addToOpenCopy(getExerciseName(), tag);
                else ExerciseTags.fireChanged(true);
            };
            TextField[] order = {field, pointsField, commentField};
            for(TextField f : order){
                f.setOnKeyPressed(k -> {
                    if(k.getCode() == KeyCode.TAB){ // Name → points → comment
                        k.consume();
                        int index = java.util.Arrays.asList(order).indexOf(f) + (k.isShiftDown() ? -1 : 1);
                        order[(index + order.length) % order.length].requestFocus();
                    }else if(k.getCode() == KeyCode.ENTER){
                        k.consume();
                        create.run();
                    }else if(k.getCode() == KeyCode.ESCAPE){
                        k.consume();
                        update();
                    }
                });
                f.focusedProperty().addListener((o, oldValue, newValue) -> {
                    // Clicked elsewhere (not in the other field): cancelled
                    if(!newValue) Platform.runLater(() -> {
                        if(!field.isFocused() && !pointsField.isFocused() && !commentField.isFocused()) update();
                    });
                });
            }
            ((Pane) add.getParent()).getChildren().set(((Pane) add.getParent()).getChildren().indexOf(add), fields);
            Platform.runLater(field::requestFocus);
        });
        return add;
    }
    // "1", "-0,5": the number of points (positive), or null
    static Double parsePoints(String text){
        String value = text == null ? "" : text.strip().replace(',', '.').replace('−', '-');
        if(value.isEmpty()) return null;
        try{
            double points = Math.abs(Double.parseDouble(value));
            return points == 0 ? null : points;
        }catch(NumberFormatException e){
            return null;
        }
    }

    private void rename(Tag tag){
        TextInputDialog dialog = new TextInputDialog(tag.getName());
        dialog.initOwner(MainWindow.mainScreen.getScene().getWindow());
        dialog.setTitle(TR.tr("tags.menu.rename"));
        dialog.setHeaderText(null);
        dialog.setContentText(TR.tr("tags.rename.label"));
        dialog.showAndWait().ifPresent(name -> {
            if(ExerciseTags.getData().rename(tag, name)) ExerciseTags.fireChanged(true);
        });
    }
    private void delete(Tag tag){
        int count = ExerciseTags.getData().getCopies(tag).size();
        if(count > 0){
            CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, TR.tr("tags.menu.delete"), (count == 1 ? TR.tr("tags.delete.header.one", tag.getName()) : TR.tr("tags.delete.header", tag.getName(), String.valueOf(count)))
                    + (tag.isWrittenOnCopy() ? "\n" + TR.tr("tags.delete.written") : ""));
            alert.addCancelButton(ButtonPosition.CLOSE);
            alert.addDeleteButton(ButtonPosition.DEFAULT);
            if(alert.getShowAndWaitGetButtonPosition(ButtonPosition.CLOSE) != ButtonPosition.DEFAULT) return;
        }
        ExerciseTags.deleteTag(tag);
    }

    // OVERVIEW

    private void loadScores(){
        if(exercise == null) return;
        String name = getExerciseName();
        ExerciseTags.loadScores(exercise, loaded -> {
            if(!Objects.equals(name, getExerciseName())) return;
            scores = loaded;
            scoresExercise = name;
            if(overviewOpen) update();
        });
    }

    // "This exercise · Whole evaluation"
    private Node buildScopes(){
        HBox scopes = new HBox(10);
        for(boolean all : new boolean[]{false, true}){
            Hyperlink scope = link(TR.tr(all ? "tags.overview.scope.all" : "tags.overview.scope.exercise"));
            if(all == overviewAll){
                scope.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + colors.text() + "; -fx-underline: false;");
                scope.setDisable(true);
                scope.setOpacity(1);
            }
            scope.setOnAction(e -> {
                overviewAll = all;
                update();
            });
            scopes.getChildren().add(scope);
        }
        return scopes;
    }

    // A tag by row: name, copies and share of the class, then a bar and the average points of these copies
    private Node buildOverview(List<Tag> tags, int copies){
        VBox rows = new VBox(8);
        double total = exercise.getCore().getTotal();
        boolean scoresReady = Objects.equals(scoresExercise, getExerciseName());
        for(Tag tag : tags){
            List<String> tagCopies = ExerciseTags.getData().getCopies(getExerciseName(), tag);
            double ratio = copies == 0 ? 0 : (double) tagCopies.size() / copies;
            
            String average = "…";
            if(scoresReady){
                OptionalDouble avg = tagCopies.stream().filter(scores::containsKey).mapToDouble(scores::get).average();
                average = avg.isPresent() ? MainWindow.gradesDigFormat.format(avg.getAsDouble()) + " / " + MainWindow.gradesDigFormat.format(total) : "–";
            }
            Label averageLabel = new Label(TR.tr("tags.overview.averageShort", average));
            averageLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            averageLabel.setMinWidth(Region.USE_PREF_SIZE);
            averageLabel.setTooltip(new Tooltip(TR.tr("tags.overview.average")));
            rows.getChildren().add(buildRow(tag, tagCopies.size(), ratio, averageLabel, false));
        }
        return rows;
    }

    // Tags used in the evaluation: copies and share of the class, then a bar and the exercises they are used in
    private Node buildGlobalOverview(int copies){
        VBox rows = new VBox(8);
        EvaluationTags data = ExerciseTags.getData();
        for(Tag tag : data.getAllTags()){
            int count = data.getCopies(tag).size();
            if(count == 0) continue;
            double ratio = copies == 0 ? 0 : (double) count / copies;
            StringJoiner exercises = new StringJoiner(" · ");
            data.countByExercise(tag).forEach((name, n) -> exercises.add(name + " ×" + n));
            Label exercisesLabel = new Label(exercises.toString());
            exercisesLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            exercisesLabel.setMinWidth(0);
            exercisesLabel.setMaxWidth(Region.USE_PREF_SIZE);
            exercisesLabel.setTooltip(new Tooltip(TR.tr("tags.overview.exercises")));
            rows.getChildren().add(buildRow(tag, count, ratio, exercisesLabel, true));
        }
        if(rows.getChildren().isEmpty()){
            Label none = new Label(TR.tr("tags.overview.none"));
            none.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            rows.getChildren().add(none);
        }
        return rows;
    }

    private Node buildRow(Tag tag, int count, double ratio, Label detail, boolean all){
        String color = getColor(tag.getKind());
        Label name = new Label(tag.getName());
        name.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12;");
        name.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label countLabel = new Label(TR.tr("tags.overview.count", String.valueOf(count), String.valueOf(Math.round(100 * ratio))));
        countLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.text() + ";");
        countLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(6, name, spacer, countLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        StackPane bar = new StackPane();
        bar.setMinHeight(8);
        bar.setMaxHeight(8);
        bar.setMinWidth(30);
        bar.setStyle("-fx-background-color: " + colors.border() + "; -fx-background-radius: 4;");
        HBox.setHgrow(bar, Priority.ALWAYS);
        Region fill = new Region();
        fill.maxWidthProperty().bind(bar.widthProperty().multiply(ratio));
        fill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.getChildren().add(fill);
        HBox bottom = new HBox(8, bar, detail);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox row = new VBox(3, top, bottom);
        row.setCursor(Cursor.HAND);
        Tooltip.install(row, new Tooltip(TR.tr("tags.chip.showCopies")));
        row.setOnMouseClicked(e -> showCopies(tag, all));
        return row;
    }
    
    // PREVIEWS

    private String getScoreDetail(String copy){
        Double score = scores.get(copy);
        if(score == null || exercise == null) return null;
        return MainWindow.gradesDigFormat.format(score) + " / " + MainWindow.gradesDigFormat.format(exercise.getCore().getTotal());
    }

    // Uses of the tag in this exercise, or in all the exercises
    private List<EvaluationTags.Use> getUses(Tag tag, boolean all){
        String exerciseName = getExerciseName();
        return ExerciseTags.getData().getUses(tag).stream().filter(use -> all || use.exercise().equals(exerciseName)).toList();
    }

    private void showCopies(Tag tag, boolean all){
        File folder = EvaluationFolders.getActiveFolder();
        if(folder == null) return;
        String exerciseName = getExerciseName();
        List<EvaluationTags.Use> uses = getUses(tag, all);
        String subHeader = (all ? TR.tr("tags.overview.scope.all") : exerciseName) + "  ·  " + TR.tr(tag.getKind() == Kind.METHOD ? "tags.kind.method" : "tags.kind.mistake");
        CommentBank.Style style = new CommentBank.Style("Open Sans", 13, true, false, toJavaFXColor(getColor(tag.getKind())), 60);
        ExerciseTags.loadScores(exercise, loaded -> {
            scores = loaded;
            scoresExercise = getExerciseName();
            // No text: a marker at the spot (the name of the tag is the title of the window). Points of this exercise only.
            // A preview by occurrence: its menu changes this occurrence.
            IdentityHashMap<CommentUsages.Usage, EvaluationTags.Use> occurrences = new IdentityHashMap<>();
            List<CommentUsages.Usage> usages = uses.stream().map(use -> {
                CommentUsages.Usage usage = new CommentUsages.Usage(folder, new File(folder, use.copy()),
                        use.placement().page(), use.placement().x(), use.placement().y(), use.exercise(), "", style,
                        use.exercise().equals(exerciseName) ? getScoreDetail(use.copy()) : null);
                occurrences.put(usage, use);
                return usage;
            }).toList();
            Button review = new Button(TR.tr(all ? "tags.menu.reviewAll" : "tags.menu.review"));
            review.setDisable(usages.isEmpty());
            CommentUsagesWindow[] window = new CommentUsagesWindow[1];
            review.setOnAction(e -> {
                window[0].close();
                startReview(tag, all);
            });
            window[0] = new CommentUsagesWindow(tag.getName(), subHeader, (onFolder, onDone) -> {
                if(!usages.isEmpty()) onFolder.accept(usages);
                onDone.run();
            }, TR.tr("tags.gallery.none"), new CommentUsagesWindow.Actions((usage, card) -> buildTagCardMenu(tag, occurrences.get(usage), card), List.of(review)));
        });
    }

    // Right-click on an occurrence of a tag: remove it from the copy, or change it to another tag (same spot and sub-grade)
    private List<MenuItem> buildTagCardMenu(Tag tag, EvaluationTags.Use use, CommentUsagesWindow.Card card){
        if(use == null || ExerciseTags.getData().getUse(use.copy(), use.id()) == null) return List.of(); // Changed meanwhile
        MenuItem remove = new MenuItem(TR.tr("tags.gallery.remove", tag.getName()));
        remove.setOnAction(e -> {
            EvaluationTags.Use removed = ExerciseTags.removeOccurrence(use.copy(), use.id());
            if(removed != null) card.setDone(TR.tr("tags.gallery.removed"), () -> ExerciseTags.restoreOccurrence(removed));
        });
        List<MenuItem> change = headed(TR.tr("tags.gallery.changeTo"), buildTagChoices(card.getWindow(), use.exercise(), tag.getKind(), other -> other != tag, other -> {
            ExerciseTags.changeOccurrence(use.copy(), use.id(), other);
            card.setDone("→ " + other.getName(), () -> ExerciseTags.changeOccurrence(use.copy(), use.id(), tag));
        }));
        ArrayList<MenuItem> items = new ArrayList<>(List.of(remove, new SeparatorMenuItem()));
        items.addAll(change);
        return items;
    }

    // Right-click on a copy without method: give it a method
    private List<MenuItem> buildUnclassifiedCardMenu(CommentUsages.Usage usage, CommentUsagesWindow.Card card){
        String copy = usage.copy().getName();
        String exerciseName = usage.exercise();
        Placement placement = Placement.exercise(usage.page());
        return headed(TR.tr("tags.gallery.addMethod"), buildTagChoices(card.getWindow(), exerciseName, Kind.METHOD, other -> other.getKind() == Kind.METHOD, method -> {
            EvaluationTags.Use added = ExerciseTags.addOccurrence(copy, exerciseName, method, placement);
            card.setDone("+ " + method.getName(), () -> ExerciseTags.removeOccurrence(copy, added.id()));
        }));
    }

    // A title, then the items (no sub-menu: they are shown directly)
    static List<MenuItem> headed(String title, List<MenuItem> items){
        MenuItem header = new MenuItem(title);
        header.setDisable(true);
        header.setStyle("-fx-font-weight: bold;");
        ArrayList<MenuItem> list = new ArrayList<>();
        list.add(header);
        list.addAll(items);
        return list;
    }

    // The tags to choose, those of the exercise first (the kind first), then "New…" of this kind
    static List<MenuItem> buildTagChoices(javafx.stage.Window owner, String exerciseName, Kind kind, java.util.function.Predicate<Tag> filter, java.util.function.Consumer<Tag> onChosen){
        EvaluationTags data = ExerciseTags.getData();
        ArrayList<MenuItem> items = new ArrayList<>();
        List<Tag> tags = data.getTags(exerciseName).stream().filter(filter).toList();
        for(boolean sameKind : new boolean[]{true, false}){
            List<Tag> group = tags.stream().filter(other -> (other.getKind() == kind) == sameKind).toList();
            if(group.isEmpty()) continue;
            if(!items.isEmpty()) items.add(new SeparatorMenuItem());
            for(Tag other : group){
                MenuItem item = new MenuItem(other.getName());
                javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4, javafx.scene.paint.Color.web(TagPicker.getColor(other.getKind())));
                item.setGraphic(dot);
                item.setOnAction(e -> onChosen.accept(other));
                items.add(item);
            }
        }
        if(!items.isEmpty()) items.add(new SeparatorMenuItem());
        MenuItem create = new MenuItem(TR.tr(kind == Kind.METHOD ? "tags.gallery.newMethod" : "tags.gallery.newMistake"));
        create.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.initOwner(owner != null ? owner : MainWindow.mainScreen.getScene().getWindow());
            dialog.setTitle(TR.tr(kind == Kind.METHOD ? "tags.add.method" : "tags.add.mistake"));
            dialog.setHeaderText(null);
            dialog.setContentText(TR.tr("tags.rename.label"));
            dialog.showAndWait().map(String::strip).filter(name -> !name.isEmpty())
                    .ifPresent(name -> onChosen.accept(ExerciseTags.getData().create(exerciseName, name, kind)));
        });
        items.add(create);
        return items;
    }

    private void showWithoutMethod(List<String> copies){
        File folder = EvaluationFolders.getActiveFolder();
        if(folder == null) return;
        Placement placement = ExerciseTags.getExercisePlacement();
        ExerciseTags.loadScores(exercise, loaded -> {
            scores = loaded;
            scoresExercise = getExerciseName();
            List<CommentUsages.Usage> usages = copies.stream().map(copy -> new CommentUsages.Usage(folder, new File(folder, copy),
                    placement.page(), placement.x(), placement.y(), getExerciseName(), "", null, getScoreDetail(copy))).toList();
            String exerciseName = getExerciseName();
            Button review = new Button(TR.tr("tags.menu.review"));
            review.setDisable(usages.isEmpty());
            CommentUsagesWindow[] window = new CommentUsagesWindow[1];
            review.setOnAction(e -> {
                window[0].close();
                TagReview.start(new TagReview.Review(TR.tr("tags.review.unclassified", exerciseName), copies, Map.of()));
            });
            window[0] = new CommentUsagesWindow(TR.tr("tags.gallery.unclassified"), exerciseName, (onFolder, onDone) -> {
                if(!usages.isEmpty()) onFolder.accept(usages);
                onDone.run();
            }, TR.tr("tags.gallery.none"), new CommentUsagesWindow.Actions(this::buildUnclassifiedCardMenu, List.of(review)));
        });
    }

    // A copy with the tag in several exercises is opened where it was put first
    private void startReview(Tag tag, boolean all){
        LinkedHashMap<String, Placement> placements = new LinkedHashMap<>();
        for(EvaluationTags.Use use : getUses(tag, all)) placements.putIfAbsent(use.copy(), use.placement());
        TagReview.start(new TagReview.Review(tag.getName(), new ArrayList<>(placements.keySet()), placements));
    }

    // "#1565c0" -> "0x1565c0ff"
    private static String toJavaFXColor(String hex){
        return "0x" + hex.replace("#", "") + "ff";
    }

    // REVIEW BANNER

    // Shown at the top of the grading panel while copies are reviewed: which ones, the position, previous/next and stop.
    public static Node createReviewBanner(Colors colors){
        Label caption = new Label();
        caption.setStyle("-fx-text-fill: rgba(255,255,255,.85); -fx-font-size: 11;");
        Label label = new Label();
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        label.setWrapText(true);
        VBox texts = new VBox(0, caption, label);
        texts.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(texts, Priority.ALWAYS);
        Button previous = new Button("‹"), next = new Button("›"), stop = new Button("✕");
        for(Button button : new Button[]{previous, next, stop}){
            button.setFocusTraversable(false);
            button.setStyle("-fx-background-color: rgba(255,255,255,.2); -fx-text-fill: white; -fx-padding: 1 8; -fx-background-radius: 4;");
        }
        previous.setOnAction(e -> TagReview.openNeighbor(-1));
        next.setOnAction(e -> TagReview.openNeighbor(1));
        stop.setOnAction(e -> TagReview.stop());
        stop.setTooltip(new Tooltip(TR.tr("tags.review.stop")));
        HBox banner = new HBox(6, texts, previous, next, stop);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(6, 8, 6, 10));
        banner.setStyle("-fx-background-color: " + colors.method() + ";");
        banner.managedProperty().bind(banner.visibleProperty());
        Runnable update = () -> {
            TagReview.Review review = TagReview.reviewProperty().get();
            banner.setVisible(review != null);
            if(review == null) return;
            int index = TagReview.getIndex();
            caption.setText(TR.tr("tags.review.banner", index < 0 ? "–" : String.valueOf(index + 1), String.valueOf(review.copies().size())));
            label.setText(review.label());
        };
        TagReview.reviewProperty().addListener((o, oldValue, newValue) -> update.run());
        MainWindow.mainScreen.statusProperty().addListener((o, oldValue, newValue) -> update.run());
        update.run();
        return banner;
    }
}
