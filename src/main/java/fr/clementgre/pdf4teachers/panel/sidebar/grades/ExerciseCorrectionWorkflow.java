/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

public class ExerciseCorrectionWorkflow {
    
    private ExerciseCorrectionWorkflow(){
    }
    
    public static OptionalInt getNavigationTarget(boolean exerciseCorrectionMode, OptionalInt selectedExercisePage, int pagesCount){
        if(!exerciseCorrectionMode || selectedExercisePage.isEmpty() || pagesCount <= 0) return OptionalInt.empty();
        return OptionalInt.of(Math.min(selectedExercisePage.getAsInt(), pagesCount - 1));
    }
    
    public static int getPrefetchLastPage(int targetPageIndex, int pagesCount){
        if(pagesCount <= 0) throw new IllegalArgumentException("pagesCount must be positive.");
        if(targetPageIndex < 0) throw new IllegalArgumentException("targetPageIndex must be positive or zero.");
        return Math.min(targetPageIndex + 1, pagesCount - 1);
    }
    
    public static int getNewGradePageIndex(boolean parentIsRoot, OptionalInt mappedExercisePage, int fallbackPageIndex, int pagesCount){
        if(pagesCount <= 0) throw new IllegalArgumentException("pagesCount must be positive.");
        if(parentIsRoot) return 0;
        
        int targetPageIndex = mappedExercisePage.orElse(fallbackPageIndex);
        if(targetPageIndex < 0) targetPageIndex = 0;
        return Math.min(targetPageIndex, pagesCount - 1);
    }
    
    public static int getGeneratedGradePageIndex(boolean summaryRow, OptionalInt mappedExercisePage, int fallbackPageIndex, int pagesCount){
        if(pagesCount <= 0) throw new IllegalArgumentException("pagesCount must be positive.");
        if(summaryRow) return 0;
        
        int targetPageIndex = mappedExercisePage.orElse(fallbackPageIndex);
        if(targetPageIndex < 0) targetPageIndex = 0;
        return Math.min(targetPageIndex, pagesCount - 1);
    }
    
    /**
     * An exercise is graded when it has at least one sub-grade and all of them have a value.
     * @param gradeValues value of each grade of a copy, by grade path (parent path + "\\" + name).
     */
    public static boolean isExerciseGraded(Map<String, Double> gradeValues, String exercisePath){
        String prefix = exercisePath + "\\";
        boolean hasLeaf = false;
        for(Map.Entry<String, Double> grade : gradeValues.entrySet()){
            String path = grade.getKey();
            if(!path.equals(exercisePath) && !path.startsWith(prefix)) continue;
            boolean isLeaf = gradeValues.keySet().stream().noneMatch(other -> other.startsWith(path + "\\"));
            if(!isLeaf) continue;
            if(grade.getValue() == null || grade.getValue() < 0) return false;
            hasLeaf = true;
        }
        return hasLeaf;
    }
    
    /**
     * Index of the first file matching the predicate, going in the direction of delta from the current file,
     * wrapping around the list. The current file is never returned.
     */
    public static OptionalInt findNeighborFile(int filesCount, int currentIndex, int delta, IntPredicate matches){
        if(filesCount <= 0 || delta == 0) return OptionalInt.empty();
        int step = delta > 0 ? 1 : -1;
        for(int i = 1; i < filesCount; i++){
            int index = Math.floorMod(currentIndex + step * i, filesCount);
            if(index != currentIndex && matches.test(index)) return OptionalInt.of(index);
        }
        return OptionalInt.empty();
    }
}
