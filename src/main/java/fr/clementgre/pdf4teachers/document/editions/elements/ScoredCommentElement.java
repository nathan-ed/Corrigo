/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.document.editions.elements;

import fr.clementgre.pdf4teachers.components.menus.NodeMenuItem;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.TagScoring;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredComment;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentPropagationDialog;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredComments;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.TextInputAlert;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * A comment of the scored comments catalog placed on a copy: "Comment (−0.5)".
 * Its points are added to the value of its sub-grade (see ScoredComments.recomputeGrade).
 * The comment and the points can be changed for this copy only (local changes), they are then kept when the entry is updated.
 */
public class ScoredCommentElement extends TextElement {

    private String scoredCommentId;
    private String gradePath;
    private String comment;
    private double points;
    private boolean localText;
    private boolean localPoints;
    // Only a comment: written without points and not counted in the grade (method or mistake with a comment only)
    private boolean noPoints;

    public ScoredCommentElement(int x, int y, int pageNumber, boolean hasPage, String text, Color color, Font font, double maxWidth,
                                String scoredCommentId, String gradePath, String comment, double points, boolean localText, boolean localPoints){
        super(x, y, pageNumber, hasPage, text, color, font, maxWidth);
        this.scoredCommentId = scoredCommentId;
        this.gradePath = gradePath;
        this.comment = comment;
        this.points = points;
        this.localText = localText;
        this.localPoints = localPoints;
    }

    public static ScoredCommentElement fromEntry(ScoredComment entry, int x, int y, int pageNumber, Color color, Font font){
        return new ScoredCommentElement(x, y, pageNumber, true, ScoredCommentGrades.render(entry.getText(), entry.getPoints(), MainWindow.gradesDigFormat),
                color, font, 0, entry.getId(), entry.getGradePath(), entry.getText(), entry.getPoints(), false, false);
    }

    // SETUP / EVENT CALL BACK

    @Override
    protected void setupBindings(){
        super.setupBindings();
        // The text is edited in the text tab like any other text: the comment and the points are read back from it.
        textProperty().addListener((observable, oldValue, newValue) -> onTextEdited(newValue));
    }

    private void onTextEdited(String text){
        Optional<ScoredCommentGrades.Parsed> parsed = ScoredCommentGrades.parse(text);
        if(noPoints && parsed.isEmpty()){ // A comment only: the text is the comment
            localText |= !text.equals(comment);
            comment = text;
            Edition.setUnsave("ScoredCommentEdited");
            return;
        }
        noPoints = false;
        String newComment = parsed.map(ScoredCommentGrades.Parsed::comment).orElse(text);
        double newPoints = parsed.map(ScoredCommentGrades.Parsed::points).orElse(points);
        boolean pointsChanged = newPoints != points;

        ScoredComment entry = getEntry();
        if(entry != null){
            localText = !newComment.equals(entry.getText());
            localPoints = newPoints != entry.getPoints();
        }else{
            localText |= !newComment.equals(comment);
            localPoints |= pointsChanged;
        }
        comment = newComment;
        points = newPoints;
        Edition.setUnsave("ScoredCommentEdited");

        if(pointsChanged && getPage() != null) ScoredComments.recomputeGrade(gradePath, false);
        ScoredComments.fireChanged(false);
    }

    @Override
    protected void setupMenu(){
        super.setupMenu();
        if(isTagElement()) return; // Changed from its method or mistake

        NodeMenuItem changePoints = new NodeMenuItem(TR.tr("scoredComments.elementMenu.changePoints"), false);
        changePoints.setToolTip(TR.tr("scoredComments.elementMenu.changePoints.tooltip"));
        NodeMenuItem reset = new NodeMenuItem(TR.tr("scoredComments.elementMenu.reset"), false);
        reset.setToolTip(TR.tr("scoredComments.elementMenu.reset.tooltip"));
        NodeMenuItem saveAsNew = new NodeMenuItem(TR.tr("scoredComments.elementMenu.saveAsNew"), false);
        saveAsNew.setToolTip(TR.tr("scoredComments.elementMenu.saveAsNew.tooltip"));
        NodeMenuItem updateEntry = new NodeMenuItem(TR.tr("scoredComments.elementMenu.updateEntry"), false);
        updateEntry.setToolTip(TR.tr("scoredComments.elementMenu.updateEntry.tooltip"));

        menu.getItems().addAll(0, List.of(changePoints, reset, saveAsNew, updateEntry, new SeparatorMenuItem()));
        menu.setOnShowing(e -> {
            boolean hasEntry = getEntry() != null;
            reset.setDisable(!hasEntry || !hasLocalChanges());
            updateEntry.setDisable(!hasEntry || !hasLocalChanges());
        });

        changePoints.setOnAction(e -> askPoints());
        reset.setOnAction(e -> {
            ScoredComment entry = getEntry();
            if(entry != null) applyEntry(entry, true);
        });
        saveAsNew.setOnAction(e -> {
            ScoredComment entry = new ScoredComment(gradePath, comment, points, getEntry() == null ? null : getEntry().getColor());
            ScoredComments.getCatalog().addAfter(entry, getEntry());
            scoredCommentId = entry.getId();
            localText = false;
            localPoints = false;
            Edition.setUnsave("ScoredCommentSavedAsNew");
            ScoredComments.fireChanged(true);
        });
        updateEntry.setOnAction(e -> {
            ScoredComment entry = getEntry();
            if(entry == null) return;
            entry.setText(comment);
            entry.setPoints(points);
            localText = false;
            localPoints = false;
            Edition.setUnsave("ScoredCommentEntryUpdated");
            ScoredComments.fireChanged(true);
            new ScoredCommentPropagationDialog(entry).show();
        });
    }

    private void askPoints(){
        TextInputAlert alert = new TextInputAlert(TR.tr("scoredComments.elementMenu.changePoints"),
                TR.tr("scoredComments.elementMenu.changePoints.header"), TR.tr("scoredComments.points"));
        alert.setText(ScoredCommentGrades.formatPoints(points, MainWindow.gradesDigFormat));
        if(!alert.getShowAndWaitIsDefaultButton()) return;

        Optional<ScoredCommentGrades.Parsed> parsed = ScoredCommentGrades.parse(alert.getText().trim());
        double newPoints;
        if(parsed.isPresent()) newPoints = parsed.get().points();
        else{
            try{
                newPoints = Double.parseDouble(alert.getText().trim().replace(",", "."));
            }catch(NumberFormatException ex){
                return;
            }
        }
        setText(ScoredCommentGrades.render(comment, newPoints, MainWindow.gradesDigFormat));
    }

    // Applies the values of its method or mistake (see TagScoring). The local changes are kept.
    public void applyTag(String gradePath, String newComment, double newPoints, boolean newNoPoints, Color color){
        String oldGradePath = this.gradePath;
        this.gradePath = gradePath;
        if(!localText) comment = newComment;
        if(!localPoints){
            points = newPoints;
            noPoints = newNoPoints;
        }
        if(!color.equals(getColor())) setColor(color);
        String text = noPoints ? comment : ScoredCommentGrades.render(comment, points, MainWindow.gradesDigFormat);
        if(!text.equals(getText())) setText(text);
        Edition.setUnsave("TagElementUpdated");
        if(getPage() != null){
            if(!oldGradePath.equals(gradePath)) ScoredComments.recomputeGrade(oldGradePath, false);
            ScoredComments.recomputeGrade(gradePath, true);
        }
    }

    // Applies the catalog entry to this comment. The local changes are kept, except if resetLocalChanges is true.
    public void applyEntry(ScoredComment entry, boolean resetLocalChanges){
        String oldGradePath = gradePath;
        if(resetLocalChanges){
            localText = false;
            localPoints = false;
        }
        gradePath = entry.getGradePath();
        if(entry.getColor() != null && !Color.web(entry.getColor()).equals(getColor())) setColor(Color.web(entry.getColor()));

        String newComment = localText ? comment : entry.getText();
        double newPoints = localPoints ? points : entry.getPoints();
        setText(ScoredCommentGrades.render(newComment, newPoints, MainWindow.gradesDigFormat));

        if(getPage() != null){
            if(!oldGradePath.equals(gradePath)) ScoredComments.recomputeGrade(oldGradePath, false);
            ScoredComments.recomputeGrade(gradePath, false);
        }
    }

    // ACTIONS

    // Selecting a scored comment keeps the grading panel visible (its text comes from its entry, not from the text tab).
    @Override
    public void select(){
        if(MainWindow.gradingTab != null && MainWindow.gradingTab.isSelected()){
            selectPartial();
            return;
        }
        super.select();
    }

    @Override
    public void addedToDocument(boolean markAsUnsave){
        super.addedToDocument(markAsUnsave);
        // Not when loading the document: the grades are saved.
        if(markAsUnsave){
            ScoredComments.recomputeGrade(gradePath, true);
            ScoredComments.fireChanged(false);
        }
    }
    @Override
    public void removedFromDocument(boolean markAsUnsave){
        super.removedFromDocument(markAsUnsave);
        // Deleted by the teacher: its method or mistake is removed from the copy
        if(markAsUnsave && isTagElement()) TagScoring.onElementDeleted(scoredCommentId);
        if(markAsUnsave){
            ScoredComments.recomputeGrade(gradePath, false);
            ScoredComments.fireChanged(false);
        }
    }

    // READER AND WRITERS

    @Override
    public LinkedHashMap<Object, Object> getYAMLData(){
        LinkedHashMap<Object, Object> data = super.getYAMLData();
        data.put(ScoredCommentGrades.KEY_ID, scoredCommentId);
        data.put(ScoredCommentGrades.KEY_GRADE_PATH, gradePath);
        data.put(ScoredCommentGrades.KEY_COMMENT, comment);
        data.put(ScoredCommentGrades.KEY_POINTS, points);
        data.put(ScoredCommentGrades.KEY_LOCAL_TEXT, localText);
        data.put(ScoredCommentGrades.KEY_LOCAL_POINTS, localPoints);
        if(noPoints) data.put(ScoredCommentGrades.KEY_NO_POINTS, true);
        return data;
    }

    static ScoredCommentElement readYAMLDataAndGive(HashMap<String, Object> data, int x, int y, int page, boolean hasPage,
                                                    String text, Color color, Font font, double maxWidth){
        ScoredCommentElement element = new ScoredCommentElement(x, y, page, hasPage, text, color, font, maxWidth,
                Config.getString(data, ScoredCommentGrades.KEY_ID),
                Config.getString(data, ScoredCommentGrades.KEY_GRADE_PATH),
                Config.getString(data, ScoredCommentGrades.KEY_COMMENT),
                Config.getDouble(data, ScoredCommentGrades.KEY_POINTS),
                Config.getBoolean(data, ScoredCommentGrades.KEY_LOCAL_TEXT),
                Config.getBoolean(data, ScoredCommentGrades.KEY_LOCAL_POINTS));
        element.noPoints = Config.getBoolean(data, ScoredCommentGrades.KEY_NO_POINTS);
        return element;
    }

    // GETTERS AND SETTERS

    public ScoredComment getEntry(){
        return ScoredComments.getCatalog().get(scoredCommentId).orElse(null);
    }
    public String getScoredCommentId(){
        return scoredCommentId;
    }
    public String getGradePath(){
        return gradePath;
    }
    public void setGradePath(String gradePath){
        this.gradePath = gradePath;
        Edition.setUnsave("ScoredCommentGradePathChanged");
    }
    public String getComment(){
        return comment;
    }
    public double getPoints(){
        return points;
    }
    // Only a comment: not counted in the grade
    public boolean isNoPoints(){
        return noPoints;
    }
    public void setNoPoints(boolean noPoints){
        this.noPoints = noPoints;
    }
    // Written for a method or a mistake (see TagScoring)
    public boolean isTagElement(){
        return TagScoring.isTagElementId(scoredCommentId);
    }
    public boolean isLocalText(){
        return localText;
    }
    public boolean isLocalPoints(){
        return localPoints;
    }
    public boolean hasLocalChanges(){
        return localText || localPoints;
    }

    // TRANSFORMATIONS

    public TextElement toPlainTextElement(){
        return new TextElement(getRealX(), getRealY(), pageNumber, true, getText(), getColor(), getFont(), getTextMaxWidth());
    }
    @Override
    public Element clone(){
        if(isTagElement()) return toPlainTextElement(); // A copy of the comment: it must not count the points twice
        return new ScoredCommentElement(getRealX(), getRealY(), pageNumber, true, getText(), getColor(), getFont(), getTextMaxWidth(),
                scoredCommentId, gradePath, comment, points, localText, localPoints);
    }
    @Override
    public Element cloneHeadless(){
        if(isTagElement()) return new TextElement(getRealX(), getRealY(), pageNumber, false, getText(), getColor(), getFont(), getTextMaxWidth());
        return new ScoredCommentElement(getRealX(), getRealY(), pageNumber, false, getText(), getColor(), getFont(), getTextMaxWidth(),
                scoredCommentId, gradePath, comment, points, localText, localPoints);
    }
}
