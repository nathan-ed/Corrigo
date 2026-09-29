/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PNFAnnotationManager {
    
    private static final Color PNF_COLOR = Color.web("#b31a1a");
    private static final Font PNF_FONT = FontUtils.getFont("Open Sans", false, false, 24);
    private static final Font PNF_TABLE_FONT = FontUtils.getFont("Open Sans", false, false, 18);
    private static final String PNF_TEXT = "PNF";
    private static final String PNF_MARK = "I";
    static final int MAX_MARKS_PER_EXERCISE = 4;
    static final int TABLE_X = 3199;
    static final int TABLE_HEADER_Y = 130459;
    static final int TABLE_ROW_STEP = (int) (Element.GRID_HEIGHT * .035);
    
    private PNFAnnotationManager(){
    }
    
    public static void addPNF(PageRenderer page, double pageX, double pageY){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        
        List<String> exercises = getExerciseNames();
        int exerciseIndex = getRowIndex(exercises, MainWindow.footerBar.getSelectedExerciseIndex());
        int rowCount = getRowCount(exercises);
        if(exerciseIndex < 0){
            MainWindow.footerBar.showToast(Color.web("#6a1b1b"), Color.WHITE, TR.tr("pnf.noExerciseSelected"));
            return;
        }
        
        // All the changes below are registered as a single undo action.
        UndoGroup undoGroup = new UndoGroup();
        incrementSummaryMark(exerciseIndex, rowCount, undoGroup);
        addPNFAnnotation(page, pageX, pageY, undoGroup);
        Edition.setUnsave("PNF annotation added");
        MainWindow.mainScreen.document.edition.save(false);
    }
    
    // The first action of the group starts a new undo step, the following ones are merged into it.
    private static class UndoGroup {
        private boolean started;
        UType next(){
            if(started) return UType.ELEMENT_NO_COUNT_BEFORE;
            started = true;
            return UType.ELEMENT;
        }
    }
    
    private static void addPNFAnnotation(PageRenderer page, double pageX, double pageY, UndoGroup undoGroup){
        TextElement element = new TextElement(page.toGridX(pageX), page.toGridY(pageY), page.getPage(),
                true, PNF_TEXT, PNF_COLOR, PNF_FONT, 0);
        page.addElement(element, true, undoGroup.next());
        element.centerOnCoordinatesY();
    }
    
    private static void incrementSummaryMark(int exerciseIndex, int rowCount, UndoGroup undoGroup){
        PageRenderer firstPage = MainWindow.mainScreen.document.getPage(0);
        ArrayList<TextElement> rows = getSummaryRows(firstPage);
        
        // Existing row: edit it first, as a text edit always registers a new undo step.
        if(exerciseIndex < rows.size()){
            undoGroup.next();
            TextElement row = rows.get(exerciseIndex);
            row.setText(addMarkToRowText(row.getText(), exerciseIndex));
        }
        
        if(getSummaryHeader(firstPage).isEmpty()){
            TextElement header = new TextElement(TABLE_X, TABLE_HEADER_Y, 0, true, PNF_TEXT, PNF_COLOR, PNF_TABLE_FONT, 0);
            firstPage.addElement(header, true, undoGroup.next());
        }
        for(int i = rows.size(); i < rowCount; i++){
            String text = i == exerciseIndex ? addMarkToRowText(getRowLabel(i), i) : getRowLabel(i);
            TextElement row = new TextElement(TABLE_X, getRowY(i), 0, true, text, PNF_COLOR, PNF_TABLE_FONT, 0);
            firstPage.addElement(row, true, undoGroup.next());
        }
    }
    
    private static Optional<TextElement> getSummaryHeader(PageRenderer firstPage){
        return firstPage.getElements().stream()
                .filter(TextElement.class::isInstance)
                .map(TextElement.class::cast)
                .filter(element -> element.getPageNumber() == 0)
                .filter(element -> PNF_TEXT.equals(element.getText()))
                .filter(element -> Math.abs(element.getRealX() - TABLE_X) < 1000)
                .filter(element -> Math.abs(element.getRealY() - TABLE_HEADER_Y) < 1000)
                .findFirst();
    }
    
    private static ArrayList<TextElement> getSummaryRows(PageRenderer firstPage){
        return firstPage.getElements().stream()
                .filter(TextElement.class::isInstance)
                .map(TextElement.class::cast)
                .filter(element -> element.getPageNumber() == 0)
                .filter(element -> isPNFMarkRow(element.getText()))
                .filter(element -> Math.abs(element.getRealX() - TABLE_X) < 1000)
                .filter(element -> element.getRealY() >= getRowY(0) - 1000)
                .sorted(Comparator.comparingInt(Element::getRealY))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    
    static String addMarkToRowText(String text, int rowIndex){
        int marks = countMarks(text);
        if(marks >= MAX_MARKS_PER_EXERCISE) return text;
        
        String label = getRowLabel(rowIndex);
        String marksText = getMarksText(text);
        return label + (marksText.isBlank() ? PNF_MARK : marksText + "  " + PNF_MARK);
    }
    
    static boolean isPNFMarkRow(String text){
        return text.matches("\\d+\\.\\s*(I\\s*)*");
    }
    
    static int countMarks(String text){
        return (int) text.chars().filter(c -> c == 'I').count();
    }
    
    static String getMarksText(String text){
        return text.replaceFirst("^\\d+\\.\\s*", "");
    }
    
    static String getRowLabel(int rowIndex){
        return (rowIndex + 1) + ". ";
    }
    
    // Names of the exercises of the grade scale (top-level grades).
    private static List<String> getExerciseNames(){
        if(GradeTreeView.getTotal() == null) return List.of();
        return GradeTreeView.getTotal().getChildren().stream()
                .map(item -> ((GradeTreeItem) item).getCore().getName())
                .toList();
    }
    
    // The PNF grade has no row in the table: the rows are the other exercises, in the order of the grade scale.
    static boolean isPNFExercise(String name){
        return name != null && name.trim().equalsIgnoreCase(PNF_TEXT);
    }
    static int getRowCount(List<String> exercises){
        return (int) exercises.stream().filter(name -> !isPNFExercise(name)).count();
    }
    // Row of the exercise at this index of the grade scale, or -1 (no exercise, or the PNF grade itself).
    static int getRowIndex(List<String> exercises, int exerciseIndex){
        if(exerciseIndex < 0 || exerciseIndex >= exercises.size() || isPNFExercise(exercises.get(exerciseIndex))) return -1;
        return (int) exercises.subList(0, exerciseIndex).stream().filter(name -> !isPNFExercise(name)).count();
    }
    
    static int getRowY(int rowIndex){
        return TABLE_HEADER_Y + TABLE_ROW_STEP * (rowIndex + 1);
    }
}
