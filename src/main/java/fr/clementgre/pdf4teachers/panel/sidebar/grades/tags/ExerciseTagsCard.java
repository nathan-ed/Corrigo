/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
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
 * "Methods & mistakes" of the exercise being graded, in the grading panel: a chip per tag (a click adds it to the copy
 * or removes it, a click on its number shows the copies that have it), the tags creation, and an overview of the class:
 * copies, share and average points by tag, and the copies without method.
 */
public class ExerciseTagsCard extends VBox {

    // Colors of the grading panel
    public record Colors(String card, String border, String text, String muted, String method, String mistake) {}

    private final Colors colors;
    private GradeTreeItem exercise;
    private Map<String, Double> scores = Map.of();
    private String scoresExercise;
    private boolean overviewOpen;
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
        for(Kind kind : Kind.values()){
            FlowPane chips = new FlowPane(5, 5);
            for(Tag tag : tags){
                if(tag.getKind() == kind) chips.getChildren().add(buildChip(tag, copy != null && data.has(copy, tag), data.getCopies(tag).size(), copies));
            }
            chips.getChildren().add(buildAdd(kind));
            Label kindLabel = new Label(TR.tr(kind == Kind.METHOD ? "tags.kind.methods" : "tags.kind.mistakes"));
            kindLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            getChildren().add(new VBox(3, kindLabel, chips));
        }

        // Class
        List<String> allCopies = ExerciseTags.getFolderCopies().stream().map(File::getName).toList();
        List<String> withoutMethod = data.getWithoutMethod(exerciseName, allCopies);
        boolean hasMethods = tags.stream().anyMatch(tag -> tag.getKind() == Kind.METHOD);
        if(hasMethods && !allCopies.isEmpty()){
            Label summary = new Label(TR.tr("tags.card.classified", String.valueOf(allCopies.size() - withoutMethod.size()), String.valueOf(allCopies.size())));
            summary.setWrapText(true);
            summary.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            VBox summaryBox = new VBox(2, summary);
            if(!withoutMethod.isEmpty()){
                Hyperlink show = link(TR.tr("tags.card.showUnclassified", String.valueOf(withoutMethod.size())));
                show.setOnAction(e -> showWithoutMethod(withoutMethod));
                Hyperlink review = link(TR.tr("tags.card.review"));
                review.setOnAction(e -> TagReview.start(new TagReview.Review(TR.tr("tags.review.unclassified", exerciseName), withoutMethod, Map.of())));
                FlowPane links = new FlowPane(10, 2, show, review);
                summaryBox.getChildren().add(links);
            }
            getChildren().add(summaryBox);
        }

        // Overview
        if(!tags.isEmpty()){
            Hyperlink toggle = link(TR.tr(overviewOpen ? "tags.card.overview.hide" : "tags.card.overview.show"));
            toggle.setOnAction(e -> {
                overviewOpen = !overviewOpen;
                if(overviewOpen) loadScores();
                update();
            });
            getChildren().add(toggle);
            if(overviewOpen) getChildren().add(buildOverview(tags, allCopies.size()));
        }
    }

    private Hyperlink link(String text){
        Hyperlink link = new Hyperlink(text);
        link.setFocusTraversable(false);
        link.setPadding(Insets.EMPTY);
        link.setStyle("-fx-font-size: 11;");
        return link;
    }

    // CHIPS

    private Node buildChip(Tag tag, boolean applied, int count, int copies){
        String color = getColor(tag.getKind());
        Label name = new Label(tag.getName());
        Label badge = new Label(String.valueOf(count));
        HBox chip = new HBox(6, name, badge);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setCursor(Cursor.HAND);
        chip.setPadding(new Insets(3, 5, 3, 10));
        if(applied){
            chip.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 14; -fx-border-color: " + color + "; -fx-border-radius: 14;");
            name.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
            badge.setStyle("-fx-text-fill: white; -fx-background-color: rgba(255,255,255,.28); -fx-background-radius: 9; -fx-padding: 0 6; -fx-font-size: 11;");
        }else{
            chip.setStyle("-fx-background-color: transparent; -fx-border-color: " + color + "; -fx-border-radius: 14; -fx-background-radius: 14;");
            name.setStyle("-fx-text-fill: " + color + ";");
            badge.setStyle("-fx-text-fill: " + color + "; -fx-background-color: " + color + "22; -fx-background-radius: 9; -fx-padding: 0 6; -fx-font-size: 11;");
        }
        badge.setCursor(Cursor.HAND);
        Tooltip.install(badge, new Tooltip(TR.tr("tags.chip.showCopies")));
        String share = copies == 0 ? "" : " (" + Math.round(100.0 * count / copies) + " %)";
        Tooltip.install(name, new Tooltip(TR.tr(applied ? "tags.chip.remove" : "tags.chip.add") + "\n" + TR.tr("tags.chip.count", String.valueOf(count)) + share));
        chip.setOnMouseClicked(e -> {
            if(e.getButton() != MouseButton.PRIMARY) return;
            if(isIn(e.getTarget(), badge)) showCopies(tag);
            else ExerciseTags.toggleOnOpenCopy(tag, ExerciseTags.getExercisePlacement());
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
        MenuItem show = new MenuItem(TR.tr("tags.menu.showCopies"));
        show.setOnAction(e -> showCopies(tag));
        MenuItem review = new MenuItem(TR.tr("tags.menu.review"));
        review.setOnAction(e -> startReview(tag));
        review.setDisable(ExerciseTags.getData().getCopies(tag).isEmpty());
        MenuItem rename = new MenuItem(TR.tr("tags.menu.rename"));
        rename.setOnAction(e -> rename(tag));
        MenuItem kind = new MenuItem(TR.tr(tag.getKind() == Kind.METHOD ? "tags.menu.toMistake" : "tags.menu.toMethod"));
        kind.setOnAction(e -> {
            ExerciseTags.getData().setKind(tag, tag.getKind() == Kind.METHOD ? Kind.MISTAKE : Kind.METHOD);
            ExerciseTags.fireChanged(true);
        });
        MenuItem delete = new MenuItem(TR.tr("tags.menu.delete"));
        delete.setOnAction(e -> delete(tag));
        return new ContextMenu(show, review, new SeparatorMenuItem(), rename, kind, new SeparatorMenuItem(), delete);
    }

    // "+ Method": a text field, Enter creates the tag and adds it to the copy
    private Node buildAdd(Kind kind){
        Label add = new Label(TR.tr(kind == Kind.METHOD ? "tags.add.method" : "tags.add.mistake"));
        add.setCursor(Cursor.HAND);
        add.setPadding(new Insets(3, 10, 3, 10));
        add.setStyle("-fx-text-fill: " + colors.muted() + "; -fx-border-color: " + colors.border() + "; -fx-border-style: dashed; -fx-border-radius: 14;");
        add.setOnMouseClicked(e -> {
            if(e.getButton() != MouseButton.PRIMARY) return;
            TextField field = new TextField();
            field.setPromptText(TR.tr(kind == Kind.METHOD ? "tags.add.method.prompt" : "tags.add.mistake.prompt"));
            field.setPrefColumnCount(14);
            field.setOnKeyPressed(k -> {
                if(k.getCode() == KeyCode.ENTER){
                    k.consume();
                    String text = field.getText().strip();
                    if(text.isEmpty()){
                        update();
                        return;
                    }
                    Tag tag = ExerciseTags.getData().create(getExerciseName(), text, kind);
                    String copy = ExerciseTags.getOpenCopy();
                    if(copy != null && !ExerciseTags.getData().has(copy, tag)) ExerciseTags.toggleOnOpenCopy(tag, ExerciseTags.getExercisePlacement());
                    else ExerciseTags.fireChanged(true);
                }else if(k.getCode() == KeyCode.ESCAPE){
                    k.consume();
                    update();
                }
            });
            field.focusedProperty().addListener((o, oldValue, newValue) -> {
                if(!newValue) Platform.runLater(this::update); // Clicked elsewhere: cancelled
            });
            ((Pane) add.getParent()).getChildren().set(((Pane) add.getParent()).getChildren().indexOf(add), field);
            Platform.runLater(field::requestFocus);
        });
        return add;
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
            CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, TR.tr("tags.menu.delete"), TR.tr("tags.delete.header", tag.getName(), String.valueOf(count)));
            alert.addCancelButton(ButtonPosition.CLOSE);
            alert.addDeleteButton(ButtonPosition.DEFAULT);
            if(alert.getShowAndWaitGetButtonPosition(ButtonPosition.CLOSE) != ButtonPosition.DEFAULT) return;
        }
        ExerciseTags.getData().delete(tag);
        ExerciseTags.fireChanged(true);
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

    // A tag by row: name, copies and share of the class, then a bar and the average points of these copies
    private Node buildOverview(List<Tag> tags, int copies){
        VBox rows = new VBox(8);
        double total = exercise.getCore().getTotal();
        boolean scoresReady = Objects.equals(scoresExercise, getExerciseName());
        for(Tag tag : tags){
            List<String> tagCopies = ExerciseTags.getData().getCopies(tag);
            double ratio = copies == 0 ? 0 : (double) tagCopies.size() / copies;
            String color = getColor(tag.getKind());
            
            Label name = new Label(tag.getName());
            name.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12;");
            name.setMinWidth(0);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label count = new Label(TR.tr("tags.overview.count", String.valueOf(tagCopies.size()), String.valueOf(Math.round(100 * ratio))));
            count.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.text() + ";");
            count.setMinWidth(Region.USE_PREF_SIZE);
            HBox top = new HBox(6, name, spacer, count);
            top.setAlignment(Pos.CENTER_LEFT);
            
            StackPane bar = new StackPane();
            bar.setMinHeight(8);
            bar.setMaxHeight(8);
            bar.setStyle("-fx-background-color: " + colors.border() + "; -fx-background-radius: 4;");
            HBox.setHgrow(bar, Priority.ALWAYS);
            Region fill = new Region();
            fill.maxWidthProperty().bind(bar.widthProperty().multiply(ratio));
            fill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            bar.getChildren().add(fill);
            String average = "…";
            if(scoresReady){
                OptionalDouble avg = tagCopies.stream().filter(scores::containsKey).mapToDouble(scores::get).average();
                average = avg.isPresent() ? MainWindow.gradesDigFormat.format(avg.getAsDouble()) + " / " + MainWindow.gradesDigFormat.format(total) : "–";
            }
            Label averageLabel = new Label(TR.tr("tags.overview.averageShort", average));
            averageLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + colors.muted() + ";");
            averageLabel.setMinWidth(Region.USE_PREF_SIZE);
            averageLabel.setTooltip(new Tooltip(TR.tr("tags.overview.average")));
            HBox bottom = new HBox(8, bar, averageLabel);
            bottom.setAlignment(Pos.CENTER_LEFT);
            
            VBox row = new VBox(3, top, bottom);
            row.setCursor(Cursor.HAND);
            Tooltip.install(row, new Tooltip(TR.tr("tags.chip.showCopies")));
            row.setOnMouseClicked(e -> showCopies(tag));
            rows.getChildren().add(row);
        }
        return rows;
    }
    
    // PREVIEWS

    private String getScoreDetail(String copy){
        Double score = scores.get(copy);
        if(score == null || exercise == null) return null;
        return MainWindow.gradesDigFormat.format(score) + " / " + MainWindow.gradesDigFormat.format(exercise.getCore().getTotal());
    }

    private void showCopies(Tag tag){
        File folder = EvaluationFolders.getActiveFolder();
        if(folder == null) return;
        List<String> copies = ExerciseTags.getData().getCopies(tag);
        String subHeader = getExerciseName() + "  ·  " + TR.tr(tag.getKind() == Kind.METHOD ? "tags.kind.method" : "tags.kind.mistake");
        CommentBank.Style style = new CommentBank.Style("Open Sans", 13, true, false, toJavaFXColor(getColor(tag.getKind())), 60);
        ExerciseTags.loadScores(exercise, loaded -> {
            scores = loaded;
            scoresExercise = getExerciseName();
            List<CommentUsages.Usage> usages = copies.stream().map(copy -> {
                Placement placement = ExerciseTags.getData().getPlacement(copy, tag);
                // No text: a marker at the spot (the name of the tag is the title of the window)
                return new CommentUsages.Usage(folder, new File(folder, copy), placement.page(), placement.x(), placement.y(),
                        getExerciseName(), "", style, getScoreDetail(copy));
            }).toList();
            new CommentUsagesWindow(tag.getName(), subHeader, (onFolder, onDone) -> {
                if(!usages.isEmpty()) onFolder.accept(usages);
                onDone.run();
            }, TR.tr("tags.gallery.none"));
        });
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
            new CommentUsagesWindow(TR.tr("tags.gallery.unclassified"), getExerciseName(), (onFolder, onDone) -> {
                if(!usages.isEmpty()) onFolder.accept(usages);
                onDone.run();
            }, TR.tr("tags.gallery.none"));
        });
    }

    private void startReview(Tag tag){
        List<String> copies = ExerciseTags.getData().getCopies(tag);
        LinkedHashMap<String, Placement> placements = new LinkedHashMap<>();
        for(String copy : copies) placements.put(copy, ExerciseTags.getData().getPlacement(copy, tag));
        TagReview.start(new TagReview.Review(tag.getName(), copies, placements));
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
