/*
 * Copyright (c) 2020-2024. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 * Modified by Nathan, 2026.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.export;

import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeRating;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import corrigo.panel.sidebar.grades.Marks;
import fr.clementgre.pdf4teachers.utils.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ExportFile {
    
    public File file;
    
    public ArrayList<GradeElement> grades = new ArrayList<>();
    public List<TextElement> comments;
    
    // Mark written on the copy (see MarksComputation), and the one computed from the grades if the copy is graded.
    private OptionalDouble writtenMark = OptionalDouble.empty();
    private OptionalDouble computedMark = OptionalDouble.empty();
    
    public ExportFile(File file, int exportTier, boolean comments) throws Exception{
        this.file = file;
        
        if(comments) this.comments = new ArrayList<>();
        File editFile = Edition.getEditFile(file);
        
        Element[] elements = Edition.simpleLoad(editFile);
        for(Element element : elements){
            if(element instanceof GradeElement){
                grades.add(((GradeElement) element));
                
            }else if(element instanceof TextElement text && text.isMark()){
                writtenMark = Marks.parseMarkText(text.getText());
            }else if(comments && element instanceof TextElement){
                this.comments.add(((TextElement) element));
            }
            
        }
        
        computeMark();
        grades.removeIf(grade -> GradeTreeView.getElementTier(grade.getParentPath()) >= exportTier);
        
        grades = GradeElement.sortGrades(grades);
    }
    
    // With all the grades: a copy is graded when all its grades without sub-grades have a value (bonus grades may be empty).
    private void computeMark(){
        Set<String> parents = new HashSet<>();
        grades.forEach(grade -> parents.add(grade.getParentPath()));
        boolean graded = grades.stream()
                .filter(grade -> !parents.contains(grade.getPath()) && !grade.isBonus())
                .allMatch(grade -> grade.getValue() >= 0);
        GradeElement root = grades.stream().filter(GradeElement::isRoot).findFirst().orElse(null);
        if(graded && root != null && root.getValue() >= 0) computedMark = OptionalDouble.of(Marks.compute(root.getValue(), root.getTotal()));
    }
    
    // The mark written on the copy, else the one computed from the grades.
    public OptionalDouble getMark(){
        return writtenMark.isPresent() ? writtenMark : computedMark;
    }
    // The copy is graded, but its written mark is missing or does not match its grades.
    public boolean isMarkOutdated(){
        return computedMark.isPresent() && (writtenMark.isEmpty() || writtenMark.getAsDouble() != computedMark.getAsDouble());
    }
    
    private int getGradeSortIndex(GradeElement grade){
        String[] parentPath = StringUtils.cleanArray(grade.getParentPath().split(Pattern.quote("\\")));
        
        if(grade.isRoot()) return 0;
        
        int index = (int) (-grade.getIndex() * Math.pow(10, parentPath.length));
        
        // parent is direct parent of grade
        index += grades.stream()
                .filter(parent -> (parent.getParentPath() + "\\" + parent.getName()).equals(grade.getParentPath()))
                .mapToInt(this::getGradeSortIndex)
                .sum();
        
        return index;
    }
    
    public boolean isSameGradeScale(ArrayList<GradeRating> gradeScale){
        int i = 0;
        for(GradeElement grade : grades){
            if(!grade.toGradeRating().containsIn(gradeScale)){
                return false;
            }
            i++;
        }
        return i == gradeScale.size();
    }
    
    public ArrayList<GradeRating> generateGradeScale(){
        
        return grades.stream()
                .map(GradeElement::toGradeRating)
                .collect(Collectors.toCollection(ArrayList::new));
    }
    
    public boolean isCompleted(){
        return grades.stream().noneMatch(grade -> grade.getValue() == -1);
    }
}
