/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.lines;

import corrigo.panel.sidebar.grades.tags.ExerciseTags;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;

import java.io.File;
import java.util.*;

/**
 * The texts of the grading by lines of the other copies of the evaluation folder, read from their edit files (read
 * again only when a file changed): the lines already used, offered while typing (LineBank).
 */
public final class OtherCopies {

    private record CachedFile(long modified, Map<?, ?> corrections) {}
    private static final HashMap<File, CachedFile> cache = new HashMap<>();

    private OtherCopies(){
    }

    // The texts of this exercise on the other copies
    public static List<String> getTexts(String exercisePath){
        ArrayList<String> texts = new ArrayList<>();
        for(Map<?, ?> corrections : getCorrections()){
            if(corrections.get(exercisePath) instanceof Map<?, ?> exercise && exercise.get("text") instanceof String text
                    && !text.isBlank()) texts.add(text);
        }
        return texts;
    }
    // The texts of all the exercises of each copy (exercise path → text), the open copy first: for the lines offered
    // everywhere in the test
    public static List<Map<String, String>> getAllTexts(){
        ArrayList<Map<String, String>> copies = new ArrayList<>();
        copies.add(LineCorrection.getTexts());
        for(Map<?, ?> corrections : getCorrections()){
            LinkedHashMap<String, String> texts = new LinkedHashMap<>();
            corrections.forEach((path, exercise) -> {
                if(path instanceof String p && exercise instanceof Map<?, ?> map && map.get("text") instanceof String text
                        && !text.isBlank()) texts.put(p, text);
            });
            copies.add(texts);
        }
        return copies;
    }

    // The "corrections" sections of the other copies
    private static List<Map<?, ?>> getCorrections(){
        ArrayList<Map<?, ?>> all = new ArrayList<>();
        if(!MainWindow.mainScreen.hasDocument(false)) return all;
        File open = MainWindow.mainScreen.document.getFile().getAbsoluteFile();
        for(File pdf : ExerciseTags.getFolderCopies()){
            if(pdf.getAbsoluteFile().equals(open)) continue;
            File editFile = Edition.getEditFile(pdf);
            if(!editFile.exists()) continue;
            CachedFile cached = cache.get(editFile);
            if(cached == null || cached.modified() != editFile.lastModified()){
                Map<?, ?> corrections = Map.of();
                try{
                    Config config = new Config(editFile);
                    config.load();
                    if(config.base.get(LineCorrection.KEY) instanceof Map<?, ?> map) corrections = map;
                }catch(Exception e){
                    Log.w("Unable to read the lines of " + editFile.getName() + ": " + e.getMessage());
                }
                cached = new CachedFile(editFile.lastModified(), corrections);
                cache.put(editFile, cached);
            }
            all.add(cached.corrections());
        }
        return all;
    }
}
