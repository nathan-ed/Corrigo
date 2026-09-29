/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.ScoredCommentElement;
import fr.clementgre.pdf4teachers.interfaces.windows.AlternativeWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches the scored comments placed on all the open files: shows the copies having a comment.
 * Double click (or Enter) on a result opens the copy at the page of the comment. The window stays open.
 */
public class ScoredCommentSearchWindow extends AlternativeWindow<VBox> {

    private record Hit(File file, int page, String comment, double points, String gradePath, boolean localChanges, String signature) {}

    private final String initialQuery;
    private final TextField search = new TextField();
    private final CheckBox onlyThisEvaluation = new CheckBox(TR.tr("scoredComments.search.onlyThisEvaluation"));
    private final Label summary = new Label();
    private final ListView<Hit> results = new ListView<>();

    private List<Hit> allHits = List.of();

    public ScoredCommentSearchWindow(String initialQuery){
        super(new VBox(), StageWidth.LARGE, TR.tr("scoredComments.search.title"), TR.tr("scoredComments.search.title"), TR.tr("scoredComments.search.subHeader"));
        this.initialQuery = initialQuery == null ? "" : initialQuery;
        // Not modal: the copies can be opened while the window stays open.
        initModality(Modality.NONE);
    }

    @Override
    public void setupSubClass(){
        search.setPromptText(TR.tr("scoredComments.search"));
        search.setText(initialQuery);
        HBox.setHgrow(search, Priority.ALWAYS);
        Button reload = new Button(TR.tr("scoredComments.search.reload"));
        reload.setOnAction(e -> reload());
        HBox top = new HBox(10, search, onlyThisEvaluation, reload);
        top.setAlignment(Pos.CENTER_LEFT);

        onlyThisEvaluation.setSelected(true);
        search.textProperty().addListener((o, oldValue, newValue) -> filter());
        onlyThisEvaluation.selectedProperty().addListener((o, oldValue, newValue) -> filter());

        summary.setStyle("-fx-text-fill: #888;");
        results.setPrefHeight(420);
        results.setPlaceholder(new Label(TR.tr("scoredComments.search.noResult")));
        results.setCellFactory(l -> new HitCell());
        results.setOnMouseClicked(e -> {
            if(e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openSelected();
        });
        results.setOnKeyPressed(e -> {
            if(e.getCode() == KeyCode.ENTER) openSelected();
        });
        VBox.setVgrow(results, Priority.ALWAYS);

        root.setSpacing(8);
        root.setPadding(new Insets(10, 20, 10, 20));
        root.getChildren().addAll(top, summary, results);

        Button close = new Button(TR.tr("scoredComments.search.close"));
        close.setOnAction(e -> close());
        setButtons(close);

        reload();
    }

    @Override
    public void afterShown(){
        Platform.runLater(() -> {
            search.requestFocus();
            search.end();
        });
    }

    // Reads the placed comments of all the open files (the open document from its current, maybe unsaved, state).
    private void reload(){
        ArrayList<Hit> hits = new ArrayList<>();
        File current = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile() : null;
        String currentSignature = ScoredComments.getCurrentSignature();

        for(File file : MainWindow.filesTab.getOpenedFiles()){
            if(file.equals(current)){
                for(ScoredCommentElement placed : ScoredComments.getPlacedComments()){
                    hits.add(new Hit(file, placed.getPageNumber(), placed.getComment(), placed.getPoints(), placed.getGradePath(),
                            placed.hasLocalChanges(), currentSignature));
                }
                continue;
            }
            File editFile = Edition.getEditFile(file);
            if(!editFile.exists()) continue;
            try{
                Config config = new Config(editFile);
                config.load();
                String signature = ScoredCommentGrades.getSignature(config.base);
                for(ScoredCommentGrades.PlacedData placed : ScoredCommentGrades.getPlacedCommentsWithPage(config.base)){
                    hits.add(new Hit(file, placed.page(), placed.getComment(), placed.getPoints(), placed.getGradePath(),
                            placed.hasLocalChanges(), signature));
                }
            }catch(Exception e){
                Log.eNotified(e, "Unable to read " + editFile.getName());
            }
        }
        allHits = hits;
        filter();
    }

    private void filter(){
        String query = search.getText() == null ? "" : search.getText().trim();
        String signature = ScoredComments.getCurrentSignature();
        List<Hit> hits = allHits.stream()
                .filter(hit -> !onlyThisEvaluation.isSelected() || signature.equals(hit.signature()))
                .filter(hit -> query.isEmpty() || ScoredCommentGrades.matchesQuery(hit.comment(), ScoredCommentEditDialog.getGradeDisplayName(hit.gradePath()), query))
                .toList();
        results.getItems().setAll(hits);
        long copies = hits.stream().map(Hit::file).distinct().count();
        long allCopies = MainWindow.filesTab.getOpenedFiles().size();
        summary.setText(TR.tr("scoredComments.search.summary", hits.size(), (int) copies, (int) allCopies));
    }

    private void openSelected(){
        Hit hit = results.getSelectionModel().getSelectedItem();
        if(hit == null) return;
        MainWindow.mainScreen.openFile(hit.file(), false, hit.page());
    }

    private static class HitCell extends ListCell<Hit> {
        @Override
        protected void updateItem(Hit hit, boolean empty){
            super.updateItem(hit, empty);
            if(empty || hit == null){
                setText(null);
                return;
            }
            setText(hit.file().getName() + "  —  " + TR.tr("scoredComments.search.page", hit.page() + 1) + "  —  "
                    + ScoredCommentGrades.render(hit.comment(), hit.points(), MainWindow.gradesDigFormat)
                    + "  (" + ScoredCommentEditDialog.getGradeDisplayName(hit.gradePath()) + ")"
                    + (hit.localChanges() ? "  ✎" : ""));
        }
    }
}
