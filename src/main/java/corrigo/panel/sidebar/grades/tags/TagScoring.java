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
            // Under the line of the spot: not over what the student wrote
            x = (int) Math.max(0, use.placement().x() - GradeElementGrid.WIDTH * .01);
            y = (int) (use.placement().y() + GradeElementGrid.HEIGHT * .012);
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
    }

    // The teacher deleted the element of an occurrence: the occurrence is removed.
    public static void onElementDeleted(String elementId){
        if(syncing) return;
        String copy = ExerciseTags.getOpenCopy();
        if(copy == null) return;
        if(ExerciseTags.getData().remove(copy, TagEditFiles.getUseId(elementId)) != null) ExerciseTags.fireChanged(true);
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
                if(!TagEditFiles.sync(config.base, data, copy, MainWindow.gradesDigFormat)) continue;
                ScoredCommentCatalog catalog = ScoredComments.getCatalogs().getOrDefault(ScoredCommentGrades.getSignature(config.base), ScoredComments.getCatalog());
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
