/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.datasaving.simpleconfigs;

import fr.clementgre.pdf4teachers.datasaving.simpleconfigs.SimpleConfig;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import corrigo.panel.sidebar.grades.ExercisePageMapping;
import corrigo.panel.sidebar.grades.Marks;
import corrigo.panel.sidebar.grades.MarksComputation;
import javafx.application.Platform;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExerciseCorrectionData extends SimpleConfig {
    
    public ExerciseCorrectionData(){
        super("exercisecorrection");
    }
    
    public static void requestSave(){
        if(MainWindow.userData == null) return;
        SimpleConfig data = MainWindow.userData.getSimpleConfig(ExerciseCorrectionData.class);
        if(data != null) data.scheduleSave();
    }
    
    @Override
    protected void manageLoadedData(Config config){
        Marks.Position markPosition = Marks.Position.fromYAML(config.base.get("markPosition"));
        Platform.runLater(() -> {
            MarksComputation.setStoredPosition(markPosition);
            if(MainWindow.footerBar == null) return;
            
            MainWindow.footerBar.setSelectedExerciseKey(config.getString("selectedExercise"));
            MainWindow.footerBar.setExerciseCorrectionMode(config.getBoolean("exerciseCorrectionMode"));
            
            for(Map.Entry<String, Object> evaluation : config.getSection("evaluations").entrySet()){
                if(!(evaluation.getValue() instanceof Map<?, ?> pageIndexes)) continue;
                ExercisePageMapping mapping = new ExercisePageMapping();
                applyPageIndexes(mapping, Config.castSection(pageIndexes));
                MainWindow.footerBar.getExercisePageMappings().put(evaluation.getKey(), mapping);
            }
            // Old format: a single mapping with Q1, Q2... keys
            HashMap<String, Object> legacyPageIndexes = config.getSection("pageIndexes");
            if(!legacyPageIndexes.isEmpty()){
                ExercisePageMapping legacy = new ExercisePageMapping();
                applyPageIndexes(legacy, legacyPageIndexes);
                MainWindow.footerBar.setLegacyExercisePageMapping(legacy);
            }
            
            
            MainWindow.footerBar.refreshExerciseChoices();
            MainWindow.filesTab.preloadNeighborExercisePages();
        });
    }
    
    @Override
    protected void unableToLoadConfig(){
    }
    
    @Override
    protected void addDataToConfig(Config config){
        if(MainWindow.footerBar == null) return;
        
        config.set("selectedExercise", MainWindow.footerBar.getSelectedExerciseKey());
        config.set("exerciseCorrectionMode", MainWindow.footerBar.isExerciseCorrectionMode());
        if(MarksComputation.getStoredPosition() != null) config.set("markPosition", MarksComputation.getStoredPosition().toYAML());
        
        LinkedHashMap<String, Object> evaluations = new LinkedHashMap<>();
        MainWindow.footerBar.getExercisePageMappings().forEach((signature, mapping) -> {
            if(!mapping.getPageIndexes().isEmpty()) evaluations.put(signature, toConfigMap(mapping));
        });
        config.set("evaluations", evaluations);

        // Keep the old format mapping until it is migrated to an evaluation.
        ExercisePageMapping legacy = MainWindow.footerBar.getLegacyExercisePageMapping();
        if(legacy != null) config.set("pageIndexes", toConfigMap(legacy));
    }
    
    static LinkedHashMap<String, Object> toConfigMap(ExercisePageMapping mapping){
        LinkedHashMap<String, Object> pageIndexes = new LinkedHashMap<>();
        mapping.getPageIndexes().forEach(pageIndexes::put);
        return pageIndexes;
    }
    
    static void applyPageIndexes(ExercisePageMapping mapping, Map<String, Object> pageIndexes){
        for(Map.Entry<String, Object> entry : pageIndexes.entrySet()){
            try{
                mapping.setPageIndex(entry.getKey(), Integer.parseInt(entry.getValue().toString()));
            }catch(NumberFormatException ignored){
                // Ignore malformed user config entries.
            }
        }
    }
}
