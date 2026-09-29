/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.datasaving.evaluation;

import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// The evaluation folders the teacher has opened a copy of, to search the comments in all of them (app data "evaluations.yml").
public final class KnownEvaluations {

    private static LinkedHashSet<String> folders;

    private KnownEvaluations(){
    }

    private static File getFile(){
        return new File(Main.dataFolder, "evaluations.yml");
    }

    private static void load(){
        if(folders != null) return;
        folders = new LinkedHashSet<>();
        File file = getFile();
        if(!file.exists()) return;
        try{
            Config config = new Config(file);
            config.load();
            for(Object folder : config.getList("folders")) if(folder != null) folders.add(folder.toString());
        }catch(Exception e){
            Log.e("Unable to read " + file + ": " + e.getMessage());
        }
    }

    // Must be called on the JavaFX thread.
    public static void add(File folder){
        if(folder == null) return;
        load();
        if(!folders.add(folder.getAbsolutePath())) return;
        try{
            new File(Main.dataFolder).mkdirs();
            Config config = new Config();
            config.set("folders", new ArrayList<>(folders));
            config.saveTo(getFile());
        }catch(Exception e){
            Log.e("Unable to save " + getFile() + ": " + e.getMessage());
        }
    }

    // The known folders that still exist. Must be called on the JavaFX thread.
    public static List<File> getFolders(){
        load();
        return folders.stream().map(File::new).filter(File::isDirectory).toList();
    }
}
