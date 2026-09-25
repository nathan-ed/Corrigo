/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.datasaving.simpleconfigs;

import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredCommentCatalog;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredComments;
import javafx.application.Platform;

import java.util.LinkedHashMap;
import java.util.Map;

// Catalogs of scored comments, by evaluation signature.
public class ScoredCommentsData extends SimpleConfig {

    public ScoredCommentsData(){
        super("scoredcomments");
    }

    public static void requestSave(){
        if(MainWindow.userData == null) return;
        SimpleConfig data = MainWindow.userData.getSimpleConfig(ScoredCommentsData.class);
        if(data != null) data.scheduleSave();
    }

    @Override
    protected void manageLoadedData(Config config){
        LinkedHashMap<String, ScoredCommentCatalog> catalogs = readCatalogs(config.getSection("evaluations"));
        Platform.runLater(() -> {
            // Catalogs created before the loading (empty) are replaced.
            ScoredComments.getCatalogs().putAll(catalogs);
            ScoredComments.fireChanged(false);
        });
    }

    @Override
    protected void unableToLoadConfig(){
    }

    @Override
    protected void addDataToConfig(Config config){
        config.set("evaluations", writeCatalogs(ScoredComments.getCatalogs()));
    }

    static LinkedHashMap<String, ScoredCommentCatalog> readCatalogs(Map<String, Object> evaluations){
        LinkedHashMap<String, ScoredCommentCatalog> catalogs = new LinkedHashMap<>();
        for(Map.Entry<String, Object> evaluation : evaluations.entrySet()){
            if(evaluation.getValue() instanceof Map<?, ?> data) catalogs.put(evaluation.getKey(), ScoredCommentCatalog.fromYAML(data));
        }
        return catalogs;
    }
    static LinkedHashMap<String, Object> writeCatalogs(Map<String, ScoredCommentCatalog> catalogs){
        LinkedHashMap<String, Object> evaluations = new LinkedHashMap<>();
        catalogs.forEach((signature, catalog) -> {
            if(!catalog.isEmpty()) evaluations.put(signature, catalog.toYAML());
        });
        return evaluations;
    }
}
