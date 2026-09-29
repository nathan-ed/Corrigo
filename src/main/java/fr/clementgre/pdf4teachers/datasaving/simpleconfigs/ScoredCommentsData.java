/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
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
// Each evaluation folder also stores its own catalogs (see ScoredComments.FolderPart), which are used first:
// this app-wide file is a backup, and the source of the catalogs of the evaluations opened before this storage existed.
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
            // Catalogs created before the loading (empty) are replaced, not the ones read from the evaluation folder.
            ScoredComments.putLoadedCatalogs(catalogs);
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

    public static LinkedHashMap<String, ScoredCommentCatalog> readCatalogs(Map<String, Object> evaluations){
        LinkedHashMap<String, ScoredCommentCatalog> catalogs = new LinkedHashMap<>();
        for(Map.Entry<String, Object> evaluation : evaluations.entrySet()){
            if(evaluation.getValue() instanceof Map<?, ?> data) catalogs.put(evaluation.getKey(), ScoredCommentCatalog.fromYAML(data));
        }
        return catalogs;
    }
    public static LinkedHashMap<String, Object> writeCatalogs(Map<String, ScoredCommentCatalog> catalogs){
        LinkedHashMap<String, Object> evaluations = new LinkedHashMap<>();
        catalogs.forEach((signature, catalog) -> {
            if(!catalog.isEmpty()) evaluations.put(signature, catalog.toYAML());
        });
        return evaluations;
    }
}
