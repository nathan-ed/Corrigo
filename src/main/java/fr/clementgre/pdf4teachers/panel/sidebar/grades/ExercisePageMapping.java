/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class ExercisePageMapping {
    
    public static final String PATH_SEPARATOR = "/";
    private final LinkedHashMap<String, Integer> pageIndexes = new LinkedHashMap<>();
    
    public static List<String> buildQuestionKeys(int questionCount){
        if(questionCount < 0) throw new IllegalArgumentException("questionCount must be positive or zero.");
        
        ArrayList<String> questions = new ArrayList<>();
        for(int i = 1; i <= questionCount; i++){
            questions.add("Q" + i);
        }
        return questions;
    }
    
    // Exercise keys are the names of the top-level grades, so that the mapping follows the exercises when they are reordered.
    public static List<String> buildExerciseKeys(List<String> exerciseNames){
        ArrayList<String> keys = new ArrayList<>();
        for(int i = 0; i < exerciseNames.size(); i++){
            String name = exerciseNames.get(i) == null ? "" : exerciseNames.get(i).trim();
            String key = name.isEmpty() ? "Q" + (i + 1) : name;
            if(keys.contains(key)) key += " (" + (i + 1) + ")";
            keys.add(key);
        }
        return keys;
    }
    // Identifies an evaluation (grade scale) by its exercises, each evaluation having its own mapping.
    public static String getSignature(List<String> exerciseKeys){
        return String.join(" | ", exerciseKeys);
    }
    // Mappings saved before the exercises were identified by name used "Q1", "Q2"... keys.
    public static ExercisePageMapping fromLegacyQuestionKeys(ExercisePageMapping legacy, List<String> exerciseKeys){
        ExercisePageMapping mapping = new ExercisePageMapping();
        for(int i = 0; i < exerciseKeys.size(); i++){
            OptionalInt page = legacy.getPageIndex("Q" + (i + 1));
            if(page.isPresent()) mapping.setPageIndex(exerciseKeys.get(i), page.getAsInt());
        }
        return mapping;
    }
    
    // The mapping sharing the most mapped exercises with these keys, or null if none share any.
    public static ExercisePageMapping findClosest(Collection<ExercisePageMapping> mappings, List<String> exerciseKeys){
        ExercisePageMapping closest = null;
        long closestCount = 0;
        for(ExercisePageMapping mapping : mappings){
            long count = exerciseKeys.stream().filter(mapping.pageIndexes::containsKey).count();
            if(count > closestCount){
                closest = mapping;
                closestCount = count;
            }
        }
        return closest;
    }
    public ExercisePageMapping copyFor(List<String> exerciseKeys){
        ExercisePageMapping copy = new ExercisePageMapping();
        for(String key : exerciseKeys){
            getPageIndex(key).ifPresent(page -> copy.setPageIndex(key, page));
        }
        return copy;
    }
    
    public void setOneBasedPage(String exerciseKey, int oneBasedPage, int pagesCount){
        if(oneBasedPage < 1 || oneBasedPage > pagesCount){
            throw new IllegalArgumentException("Page must be between 1 and " + pagesCount + ".");
        }
        setPageIndex(exerciseKey, oneBasedPage - 1);
    }
    
    public void setPageIndex(String exerciseKey, int pageIndex){
        validateExerciseKey(exerciseKey);
        if(pageIndex < 0) throw new IllegalArgumentException("pageIndex must be positive or zero.");
        
        pageIndexes.put(exerciseKey, pageIndex);
    }
    
    public void clearPageIndex(String exerciseKey){
        validateExerciseKey(exerciseKey);
        pageIndexes.remove(exerciseKey);
    }
    
    public OptionalInt getPageIndex(String exerciseKey){
        Integer pageIndex = pageIndexes.get(exerciseKey);
        if(pageIndex == null) return OptionalInt.empty();
        return OptionalInt.of(pageIndex);
    }
    
    public OptionalInt resolvePageIndex(String gradePath){
        validateExerciseKey(gradePath);
        
        String path = gradePath;
        while(!path.isEmpty()){
            OptionalInt exact = getPageIndex(path);
            if(exact.isPresent()) return exact;
            
            int separator = path.lastIndexOf(PATH_SEPARATOR);
            if(separator < 0) break;
            path = path.substring(0, separator);
        }
        return OptionalInt.empty();
    }
    
    public int resolvePageIndexOrDefault(String gradePath, int fallbackPageIndex){
        return resolvePageIndex(gradePath).orElse(fallbackPageIndex);
    }
    
    public Map<String, Integer> getPageIndexes(){
        return Collections.unmodifiableMap(pageIndexes);
    }
    
    public static String childKey(String parent, String child){
        validateExerciseKey(parent);
        validateExerciseKey(child);
        return parent + PATH_SEPARATOR + child;
    }
    
    private static void validateExerciseKey(String exerciseKey){
        if(exerciseKey == null || exerciseKey.isBlank()){
            throw new IllegalArgumentException("exerciseKey must not be blank.");
        }
    }
}
