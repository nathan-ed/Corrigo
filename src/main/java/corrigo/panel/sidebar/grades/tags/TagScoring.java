/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.datasaving.Config;
import corrigo.datasaving.evaluation.EvaluationFolders;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import corrigo.document.editions.elements.ScoredCommentElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import corrigo.panel.sidebar.grades.QuickGradePlacement;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentCatalog;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentGrades;
import corrigo.panel.sidebar.grades.scoredcomments.ScoredComments;
import corrigo.panel.sidebar.grades.tags.EvaluationTags.Target;
import corrigo.panel.sidebar.grades.tags.EvaluationTags.Use;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.*;

/**
 * Writes the methods and mistakes that have points or a comment on the copies (see TagEditFiles): the open copy
 * through its elements, the other copies of the evaluation through their edit files, whose grades are computed again.
 */
public final class TagScoring {

    private TagScoring(){
    }

    // The elements are being changed to match the occurrences: their removal is not a deletion by the teacher
    private static boolean syncing;

    public static boolean isTagElementId(String id){
        return TagEditFiles.isTagElementId(id);
    }

    // Makes the copies match their occurrences: the open copy (always) and these other copies of the evaluation.
    public static void sync(Collection<String> copies){
        String open = ExerciseTags.getOpenCopy();
        // Occurrences made before the points: they count on the first sub-grade of their exercise
        boolean filled = false;
        for(String copy : copies) filled |= ExerciseTags.getData().fillGrades(copy, ExerciseTags::getFirstGrade);
        if(open != null) filled |= ExerciseTags.getData().fillGrades(open, ExerciseTags::getFirstGrade);
        if(filled) EvaluationFolders.requestSave(ExerciseTags.FOLDER_PART);
        syncOpenCopy();
        ArrayList<String> others = new ArrayList<>(copies);
        others.remove(open);
        if(!others.isEmpty()) syncOtherCopies(others);
    }

    // OPEN COPY

    public static void syncOpenCopy(){
        String copy = ExerciseTags.getOpenCopy();
        if(copy == null || !MainWindow.mainScreen.hasDocument(false)) return;
        LinkedHashMap<String, TagEditFiles.Expected> expected = TagEditFiles.getExpected(ExerciseTags.getData(), copy);
        syncing = true;
        try{
            Set<String> present = new HashSet<>();
            for(ScoredCommentElement element : ScoredComments.getPlacedComments()){
                String id = element.getScoredCommentId();
                if(!TagEditFiles.isTagElementId(id)) continue;
                TagEditFiles.Expected wanted = expected.get(id);
                // Not an occurrence any more, written twice, or for another sub-grade now (written again next to its grade)
                if(wanted == null || !wanted.target().grade().equals(element.getGradePath()) || !present.add(id)){
                    element.delete(true, UType.NO_UNDO);
                    continue;
                }
                Target target = wanted.target();
                element.applyTag(target.grade(), target.comment() == null ? "" : target.comment(), TagEditFiles.getPoints(target),
                        target.points() == null, Color.web(TagEditFiles.getColor(wanted.tag())));
            }
            for(Map.Entry<String, TagEditFiles.Expected> entry : expected.entrySet()){
                if(!present.contains(entry.getKey())) place(entry.getKey(), entry.getValue());
            }
        }finally{
            syncing = false;
        }
    }

    // Writes an element of an occurrence on the open copy: next to its spot (on the sub-grade it was put for),
    // or in the column of comments of its grade.
    private static void place(String id, TagEditFiles.Expected wanted){
        Use use = wanted.use();
        Target target = wanted.target();
        int pageCount = MainWindow.mainScreen.document.getPagesNumber();
        PageRenderer page = MainWindow.mainScreen.document.getPage(Math.clamp(use.placement().page(), 0, pageCount - 1));
        int x, y;
        if(!use.placement().isExerciseSpot() && target.key().isEmpty()){
            double[] spot = TagEditFiles.getSpotPosition(use.placement(), target);
            x = (int) spot[0];
            y = (int) spot[1];
        }else{
            GradeElement grade = ScoredComments.findGrade(target.grade());
            QuickGradePlacement.Spot spot = QuickGradePlacement.nextSpot(grade, page.getPage());
            page = spot.page();
            x = spot.x();
            y = spot.y();
        }
        ScoredCommentElement element = new ScoredCommentElement(x, y, page.getPage(), true,
                TagEditFiles.render(target, MainWindow.gradesDigFormat), Color.web(TagEditFiles.getColor(wanted.tag())), ScoredComments.FONT,
                QuickGradePlacement.COLUMN_WIDTH, id, target.grade(),
                target.comment() == null ? "" : target.comment(), TagEditFiles.getPoints(target), false, false);
        element.setNoPoints(target.points() == null);
        page.addElement(element, true, UType.NO_UNDO);
        // A comment written at the spot goes to the free place the closest to it (a tick stays on its spot)
        if(!use.placement().isExerciseSpot() && target.key().isEmpty() && !ScoredCommentGrades.CHECK.equals(target.comment())){
            moveToFreeSpotWhenLaidOut(element, page, 20);
        }
    }

    // Once the element has its size (its text is laid out after it is added)
    // Moves it now if its size can be known now (the copy is about to be closed), else once it is laid out
    public static void moveToFreeSpotNowOrLater(TextElement element, PageRenderer page){
        element.applyCss();
        element.autosize();
        if(element.getLayoutBounds().getWidth() > 0) moveToFreeSpot(element, page);
        else moveToFreeSpotWhenLaidOut(element, page, 20);
    }
    public static void moveToFreeSpotWhenLaidOut(TextElement element, PageRenderer page, int tries){
        Platform.runLater(() -> {
            if(element.getLayoutBounds().getWidth() <= 0 && tries > 0) moveToFreeSpotWhenLaidOut(element, page, tries - 1);
            else moveToFreeSpot(element, page);
        });
    }
    // Size of a cell of the grid of the free places, in pixels of the page
    private static final double CELL = 4;
    // Moves the element to the free place of the page the closest to where it is: no ink of the student under it (from
    // the rendered page), no other annotation. Stays where it is if there is none near.
    private static void moveToFreeSpot(TextElement element, PageRenderer page){
        if(element.getPage() != page) return;
        double pageWidth = page.getWidth(), pageHeight = page.getHeight();
        double width = element.getLayoutBounds().getWidth(), height = element.getLayoutBounds().getHeight();
        if(width <= 0 || height <= 0 || pageWidth <= 0 || pageHeight <= 0) return;
        int cols = (int) (pageWidth / CELL), rows = (int) (pageHeight / CELL);
        boolean[][] occupied = new boolean[rows][cols];
        // The ink of the student, from the rendered page (a page not rendered yet: only the other annotations)
        Image image = page.hasRenderedImage() ? page.getRenderedImage() : null;
        PixelReader reader = image == null ? null : image.getPixelReader();
        if(reader != null){
            double scaleX = image.getWidth() / pageWidth, scaleY = image.getHeight() / pageHeight;
            for(int y = 0; y < rows; y++){
                for(int x = 0; x < cols; x++){
                    int dark = 0;
                    for(int i = 0; i < 3 && dark < 2; i++){
                        for(int j = 0; j < 3 && dark < 2; j++){
                            int px = (int) Math.min(image.getWidth() - 1, (x + (i + .5) / 3) * CELL * scaleX);
                            int py = (int) Math.min(image.getHeight() - 1, (y + (j + .5) / 3) * CELL * scaleY);
                            Color color = reader.getColor(px, py);
                            if(.2126 * color.getRed() + .7152 * color.getGreen() + .0722 * color.getBlue() < .75) dark++;
                        }
                    }
                    occupied[y][x] = dark >= 2;
                }
            }
            occupied = FreeSpot.removeSpecks(occupied, 3); // Dust of the scan: not writing
        }
        for(Element other : page.getElements()){
            if(other == element) continue;
            int x0 = (int) (other.getLayoutX() / CELL), y0 = (int) (other.getLayoutY() / CELL);
            int x1 = (int) Math.ceil((other.getLayoutX() + other.getLayoutBounds().getWidth()) / CELL);
            int y1 = (int) Math.ceil((other.getLayoutY() + other.getLayoutBounds().getHeight()) / CELL);
            for(int y = Math.max(0, y0); y < Math.min(rows, y1); y++){
                for(int x = Math.max(0, x0); x < Math.min(cols, x1); x++) occupied[y][x] = true;
            }
        }
        int[] free = FreeSpot.find(FreeSpot.grow(occupied, 1), (int) Math.ceil(width / CELL), (int) Math.ceil(height / CELL),
                (int) (element.getLayoutX() / CELL), (int) (element.getLayoutY() / CELL), (int) (pageWidth * .25 / CELL));
        if(free == null) return;
        element.setRealX(page.toGridX(free[0] * CELL));
        element.setRealY(page.toGridY(free[1] * CELL));
        Edition.setUnsave("TagCommentPlaced");
    }

    // The teacher deleted the element of an occurrence: the occurrence is removed.
    public static void onElementDeleted(String elementId){
        if(syncing) return;
        String copy = ExerciseTags.getOpenCopy();
        if(copy == null) return;
        ExerciseTags.onOccurrenceElementDeleted(copy, TagEditFiles.getUseId(elementId));
    }

    // OTHER COPIES

    private static void syncOtherCopies(List<String> copies){
        File folder = EvaluationFolders.getActiveFolder();
        if(folder == null) return;
        EvaluationTags data = ExerciseTags.getData();
        boolean changed = false;
        for(String copy : copies){
            File editFile = Edition.getEditFile(new File(folder, copy));
            if(!editFile.exists()) continue; // Never opened: no grade scale, nothing to write
            try{
                Config config = new Config(editFile);
                config.load();
                ScoredCommentCatalog catalog = ScoredComments.getCatalogs().getOrDefault(ScoredCommentGrades.getSignature(config.base), ScoredComments.getCatalog());
                ScoredCommentGrades.keepStoredValues(config.base, catalog); // Graded with a previous version: its other grades stay
                if(!TagEditFiles.sync(config.base, data, copy, MainWindow.gradesDigFormat)) continue;
                ScoredCommentGrades.recomputeGrades(config.base, catalog, GradeElement::isBonus);
                config.save();
                Edition.removePreloadedEditFile(editFile);
                changed = true;
            }catch(Exception e){
                Log.eNotified(e, "Unable to update the methods and mistakes written on " + copy);
            }
        }
        if(changed) MainWindow.filesTab.refresh(); // Their grades may have changed
    }

    // Grid size of a page (Element.GRID_WIDTH / GRID_HEIGHT)
    private static final class GradeElementGrid {
        static final double WIDTH = 165400, HEIGHT = 233900;
    }
}
