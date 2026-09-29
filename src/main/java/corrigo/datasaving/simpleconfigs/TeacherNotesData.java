/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.datasaving.simpleconfigs;

import fr.clementgre.pdf4teachers.datasaving.simpleconfigs.SimpleConfig;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import corrigo.panel.sidebar.notes.TeacherNote;
import corrigo.panel.sidebar.notes.TeacherNotes;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Notes of the teacher taken with no document open (see TeacherNotes); the others are stored in their evaluation folder.
// The screenshots are PNG files in TeacherNotes.getImagesFolder(null).
public class TeacherNotesData extends SimpleConfig {

    public TeacherNotesData(){
        super("teachernotes");
    }

    public static void requestSave(){
        if(MainWindow.userData == null) return;
        SimpleConfig data = MainWindow.userData.getSimpleConfig(TeacherNotesData.class);
        if(data != null) data.scheduleSave();
    }
    // Must be called on the JavaFX thread.
    public static void saveNow(){
        if(MainWindow.userData == null) return;
        SimpleConfig data = MainWindow.userData.getSimpleConfig(TeacherNotesData.class);
        if(data != null) data.saveData();
    }

    @Override
    protected void manageLoadedData(Config config){
        List<TeacherNote> notes = new ArrayList<>();
        for(Object note : config.getList("notes")){
            if(note instanceof Map<?, ?> data) notes.add(TeacherNote.fromYAML(data, null));
        }
        Platform.runLater(() -> TeacherNotes.addAppNotes(notes));
    }

    @Override
    protected void unableToLoadConfig(){
    }

    @Override
    protected void addDataToConfig(Config config){
        config.set("notes", new ArrayList<>(TeacherNotes.getAppNotes().stream().map(TeacherNote::toYAML).toList()));
    }
}
