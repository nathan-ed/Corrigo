/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.datasaving.evaluation;

import fr.clementgre.pdf4teachers.datasaving.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationFoldersTest {

    // Keeps a list of strings, like the notes or comments of an evaluation.
    private static class ListPart implements EvaluationFolders.Part {
        final List<String> items = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        @Override public String getFileName(){
            return "test";
        }
        @Override public void load(File folder, Config config){
            events.add("load " + folder.getName());
            for(Object item : config.getList("items")) items.add(item.toString());
        }
        @Override public void unload(File folder){
            events.add("unload " + folder.getName());
            items.clear();
        }
        @Override public void write(File folder, Config config){
            config.set("items", new ArrayList<>(items));
        }
        @Override public boolean isEmpty(){
            return items.isEmpty();
        }
    }

    @BeforeEach
    void reset(){
        EvaluationFolders.reset();
    }
    
    @Test
    void savesInTheFolderOfTheCopiesAndReloadsWhenSwitching(@TempDir File dir) throws Exception{
        File e1 = new File(dir, "E1"), e2 = new File(dir, "E2");
        e1.mkdirs();
        e2.mkdirs();
        ListPart part = new ListPart();
        EvaluationFolders.register(part);

        EvaluationFolders.activateFor(new File(e1, "1_A.pdf"));
        assertEquals(e1.getAbsoluteFile(), EvaluationFolders.getActiveFolder());
        EvaluationFolders.saveAllNow();
        assertFalse(new File(e1, EvaluationFolders.DATA_DIR).exists(), "Nothing to save: no folder created");

        part.items.add("comment of E1");
        EvaluationFolders.activateFor(new File(e1, "2_B.pdf")); // Same folder: nothing happens
        assertEquals(List.of("load E1"), part.events);

        EvaluationFolders.activateFor(new File(e2, "1_C.pdf")); // Saves E1, then loads E2
        assertEquals(List.of("load E1", "unload E1", "load E2"), part.events);
        File saved = new File(e1, ".pdf4teachers/test.yml");
        assertTrue(saved.exists());
        assertTrue(Files.readString(saved.toPath()).contains("comment of E1"));
        assertTrue(part.items.isEmpty());

        EvaluationFolders.activateFor(new File(e1, "1_A.pdf"));
        assertEquals(List.of("comment of E1"), part.items);
        assertFalse(new File(e1, ".pdf4teachers/test.yml.tmp").exists());
    }
    
    @Test
    void aPartSavingWhileLoadingWritesIntoTheNewFolder(@TempDir File dir) throws Exception{
        File e1 = new File(dir, "E1"), e2 = new File(dir, "E2");
        e1.mkdirs();
        e2.mkdirs();
        // Like the notes migration: loading a folder adds data to it and saves it at once.
        ListPart part = new ListPart() {
            @Override public void load(File folder, Config config){
                super.load(folder, config);
                if(folder.getName().equals("E2")){
                    items.add("migrated into E2");
                    EvaluationFolders.saveNow(this);
                }
            }
        };
        EvaluationFolders.register(part);
        EvaluationFolders.activateFor(new File(e1, "1_A.pdf"));
        part.items.add("comment of E1");
        EvaluationFolders.activateFor(new File(e2, "1_C.pdf"));
        
        assertTrue(Files.readString(new File(e1, ".pdf4teachers/test.yml").toPath()).contains("comment of E1"));
        assertFalse(Files.readString(new File(e1, ".pdf4teachers/test.yml").toPath()).contains("migrated"));
        assertTrue(Files.readString(new File(e2, ".pdf4teachers/test.yml").toPath()).contains("migrated into E2"));
    }
}
