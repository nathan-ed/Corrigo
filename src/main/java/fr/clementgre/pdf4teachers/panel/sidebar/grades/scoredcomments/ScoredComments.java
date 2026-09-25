/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import fr.clementgre.pdf4teachers.datasaving.simpleconfigs.ScoredCommentsData;
import fr.clementgre.pdf4teachers.document.Document;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.ScoredCommentElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.ExercisePageMapping;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.*;

/**
 * Scored comments: comments that add or remove points to a sub-grade.
 * Each evaluation (identified by the signature of its grade scale) has its own catalog of comments,
 * and each copy has its placed comments (ScoredCommentElement).
 */
public class ScoredComments {

    public static final Color NEGATIVE_COLOR = Color.web("#c62828");
    public static final Color POSITIVE_COLOR = Color.web("#2e7d32");
    public static final Font FONT = FontUtils.getFont("Open Sans", false, false, 18);

    // Catalogs by evaluation signature
    private static final LinkedHashMap<String, ScoredCommentCatalog> catalogs = new LinkedHashMap<>();
    // Incremented when the catalog or the placed comments change, to refresh the UI.
    private static final LongProperty revision = new SimpleLongProperty();
    // Entry to place with the next click on a page
    private static final ObjectProperty<ScoredComment> armed = new SimpleObjectProperty<>();

    private static Document lastDocument;
    private static String lastSignature;

    private ScoredComments(){
    }

    // CATALOG

    public static Map<String, ScoredCommentCatalog> getCatalogs(){
        return catalogs;
    }

    public static String getCurrentSignature(){
        if(MainWindow.gradeTab == null || GradeTreeView.getTotal() == null) return "";
        List<String> names = GradeTreeView.getTotal().getChildren().stream()
                .map(item -> ((GradeTreeItem) item).getCore().getName())
                .toList();
        return ExercisePageMapping.getSignature(ExercisePageMapping.buildExerciseKeys(names));
    }

    // Catalog of the evaluation of the open document.
    public static ScoredCommentCatalog getCatalog(){
        String signature = getCurrentSignature();
        Document document = MainWindow.mainScreen == null ? null : MainWindow.mainScreen.document;

        // The grade scale of the open document has been edited (exercise renamed, added...): the catalog follows it.
        if(document != null && document == lastDocument && lastSignature != null && !lastSignature.equals(signature)
                && !catalogs.containsKey(signature) && catalogs.containsKey(lastSignature)){
            catalogs.put(signature, catalogs.remove(lastSignature));
            requestSave();
        }
        lastDocument = document;
        lastSignature = signature;

        return catalogs.computeIfAbsent(signature, s -> {
            // The grade scale changed a bit since the catalog was made: start from the closest one.
            ScoredCommentCatalog closest = ScoredCommentCatalog.findClosest(catalogs, s);
            return closest == null ? new ScoredCommentCatalog() : closest.copy();
        });
    }

    public static void requestSave(){
        ScoredCommentsData.requestSave();
    }
    public static LongProperty revisionProperty(){
        return revision;
    }
    // Refreshes the UI, and saves the catalog if it changed.
    public static void fireChanged(boolean catalogChanged){
        if(catalogChanged) requestSave();
        revision.set(revision.get() + 1);
    }

    // ARMED ENTRY

    public static ObjectProperty<ScoredComment> armedProperty(){
        return armed;
    }
    public static ScoredComment getArmed(){
        return armed.get();
    }
    public static void arm(ScoredComment entry){
        armed.set(entry);
    }
    public static void disarm(){
        armed.set(null);
    }

    // PLACED COMMENTS

    public static List<ScoredCommentElement> getPlacedComments(){
        if(MainWindow.mainScreen == null || !MainWindow.mainScreen.hasDocument(false)) return List.of();
        ArrayList<ScoredCommentElement> placed = new ArrayList<>();
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            page.getElements().stream()
                    .filter(ScoredCommentElement.class::isInstance)
                    .map(ScoredCommentElement.class::cast)
                    .forEach(placed::add);
        }
        return placed;
    }
    public static long countPlaced(String entryId){
        return getPlacedComments().stream().filter(c -> entryId.equals(c.getScoredCommentId())).count();
    }

    public static Color getDefaultColor(double points){
        return points < 0 ? NEGATIVE_COLOR : POSITIVE_COLOR;
    }
    public static Color getColor(ScoredComment entry){
        return entry.getColor() == null ? getDefaultColor(entry.getPoints()) : Color.web(entry.getColor());
    }

    // Places an entry on a page, the pageX and pageY coordinates being the left and the vertical center of the comment.
    public static ScoredCommentElement place(ScoredComment entry, PageRenderer page, double pageX, double pageY){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        ScoredCommentElement element = ScoredCommentElement.fromEntry(entry, page.toGridX(pageX), page.toGridY(pageY), page.getPage(), getColor(entry), FONT);
        page.addElement(element, true, UType.ELEMENT);
        element.centerOnCoordinatesY();
        return element;
    }
    // Places an entry at the mouse position, or at the top of the current page.
    public static ScoredCommentElement placeAtMouse(ScoredComment entry){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        PageRenderer page = MainWindow.mainScreen.document.getLastCursorOverPageObject();
        if(page == null) return null;
        double x = page.getMouseX() <= 0 ? 60 : page.getMouseX();
        double y = page.getMouseY() <= 0 ? 60 : page.getMouseY();
        return place(entry, page, x, y);
    }

    // GRADES

    public static GradeElement findGrade(String gradePath){
        if(MainWindow.gradeTab == null || GradeTreeView.getTotal() == null) return null;
        return GradeTreeView.getGradesArray(GradeTreeView.getTotal()).stream()
                .map(GradeTreeItem::getCore)
                .filter(grade -> grade.getPath().equals(gradePath))
                .findFirst().orElse(null);
    }

    /**
     * Sets the value of a sub-grade from its placed comments.
     * @param switchToComments If the grade value was typed, it is computed from the comments from now on.
     *                         Otherwise, a typed value is kept.
     */
    public static void recomputeGrade(String gradePath, boolean switchToComments){
        GradeElement grade = findGrade(gradePath);
        if(grade == null || grade.getGradeTreeItem() == null || grade.getGradeTreeItem().hasSubGrade()) return;

        List<Double> points = getPlacedComments().stream()
                .filter(c -> gradePath.equals(c.getGradePath()))
                .map(ScoredCommentElement::getPoints)
                .toList();

        if(points.isEmpty()){
            if(grade.isValueFromComments()) grade.setComputedValue(-1);
            return;
        }
        if(!grade.isValueFromComments() && !switchToComments) return;

        double value = ScoredCommentGrades.computeLeaf(grade.getTotal(), getCatalog().getBase(gradePath), points);
        grade.setComputedValue(value);
    }

    public static void setBase(String gradePath, ScoredCommentCatalog.Base base){
        getCatalog().setBase(gradePath, base);
        recomputeGrade(gradePath, false);
        fireChanged(true);
    }

    public static void onGradeRenamed(String oldPath, String newPath){
        if(oldPath.equals(newPath) || MainWindow.mainScreen == null || !MainWindow.mainScreen.hasDocument(false)) return;
        if(getCatalog().renameGradePath(oldPath, newPath)) fireChanged(true);
        for(ScoredCommentElement placed : getPlacedComments()){
            String renamed = ScoredCommentCatalog.renamePath(placed.getGradePath(), oldPath, newPath);
            if(renamed != null) placed.setGradePath(renamed);
        }
    }

    // Creates an entry from a text of the document, and replaces the text by the scored comment.
    public static void convertTextElement(TextElement text){
        if(!MainWindow.mainScreen.hasDocument(false) || text.getPage() == null) return;
        Optional<ScoredCommentGrades.Parsed> parsed = ScoredCommentGrades.parse(text.getText());
        ScoredComment template = new ScoredComment(MainWindow.gradeTab.scoredCommentPanel.getDefaultGradePath(),
                parsed.map(ScoredCommentGrades.Parsed::comment).orElse(text.getText()), parsed.map(ScoredCommentGrades.Parsed::points).orElse(-.5), null);
        
        ScoredCommentEditDialog.show(template, true).ifPresent(entry -> {
            getCatalog().add(entry);
            PageRenderer page = text.getPage();
            ScoredCommentElement element = ScoredCommentElement.fromEntry(entry, text.getRealX(), text.getRealY(), text.getPageNumber(), getColor(entry), text.getFont());
            page.addElement(element, true, UType.ELEMENT);
            text.delete(true, UType.ELEMENT_NO_COUNT_BEFORE);
            fireChanged(true);
        });
    }
    
    /**
     * Removes an entry from the catalog, and its comments from the open document.
     * @param keepAsText The comments are turned into plain texts, and the grades they changed keep their value (they become typed values).
     */
    public static void deleteEntry(ScoredComment entry, boolean keepAsText){
        getCatalog().remove(entry.getId());
        UType undoType = UType.ELEMENT;
        for(ScoredCommentElement placed : getPlacedComments()){
            if(!entry.getId().equals(placed.getScoredCommentId())) continue;
            PageRenderer page = placed.getPage();
            if(keepAsText){
                GradeElement grade = findGrade(placed.getGradePath());
                if(grade != null) grade.setValueFromComments(false);
                page.addElement(placed.toPlainTextElement(), true, undoType);
                undoType = UType.ELEMENT_NO_COUNT_BEFORE;
            }
            placed.delete(true, undoType);
            undoType = UType.ELEMENT_NO_COUNT_BEFORE;
        }
        Edition.setUnsave("ScoredCommentDeleted");
        fireChanged(true);
    }
}
