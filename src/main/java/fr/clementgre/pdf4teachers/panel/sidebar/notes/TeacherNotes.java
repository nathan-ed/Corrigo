/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.notes;

import fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation.EvaluationComments;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.datasaving.evaluation.EvaluationFolders;
import fr.clementgre.pdf4teachers.datasaving.simpleconfigs.TeacherNotesData;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notes of the teacher, for themselves only: ideas, remarks on a student, things to discuss in class...
 * Captured from anywhere with Ctrl+Shift+N (text) or Ctrl+Shift+S (screenshot of a region of the copy, then text).
 * Each note remembers the copy, page and exercise it was taken on, to go back there.
 * The notes are stored with the evaluation of their copy (see EvaluationFolders), or in the app data when no document was open.
 */
public final class TeacherNotes {

    // Notes of the active evaluation folder, then the notes stored in the app data (taken with no document open)
    private static final ObservableList<TeacherNote> notes = FXCollections.observableArrayList();
    // Incremented when a note is edited (the list does not change)
    private static final SimpleIntegerProperty revision = new SimpleIntegerProperty();
    private static final Map<String, Image> thumbnails = new HashMap<>();
    
    // Notes of the evaluation folder, in ".pdf4teachers/notes.yml", the screenshots in ".pdf4teachers/notes/".
    public static final EvaluationFolders.Part FOLDER_PART = new EvaluationFolders.Part() {
        @Override public String getFileName(){
            return "notes";
        }
        @Override public void load(File folder, Config config){
            List<TeacherNote> loaded = new ArrayList<>();
            for(Object data : config.getList("notes")){
                if(data instanceof Map<?, ?> map) loaded.add(TeacherNote.fromYAML(map, folder));
            }
            notes.addAll(0, loaded);
            migrateAppNotes(folder);
        }
        @Override public void unload(File folder){
            notes.removeIf(note -> {
                if(!folder.equals(note.getFolder())) return false;
                if(note.getImage() != null) thumbnails.remove(getImageKey(note));
                return true;
            });
        }
        @Override public void write(File folder, Config config){
            config.set("notes", new ArrayList<>(notes.stream().filter(note -> folder.equals(note.getFolder())).map(TeacherNote::toYAML).toList()));
        }
        @Override public boolean isEmpty(){
            File folder = EvaluationFolders.getActiveFolder();
            return notes.stream().noneMatch(note -> folder != null && folder.equals(note.getFolder()));
        }
    };
    
    private TeacherNotes(){
    }
    
    public static ObservableList<TeacherNote> getNotes(){
        return notes;
    }
    public static ReadOnlyIntegerProperty revisionProperty(){
        return revision;
    }
    
    // Notes stored in the app data (TeacherNotesData).
    public static List<TeacherNote> getAppNotes(){
        return notes.stream().filter(note -> note.getFolder() == null).toList();
    }
    // Notes loaded from the app data file: those about the copies of the active folder are moved into it.
    public static void addAppNotes(List<TeacherNote> loaded){
        notes.addAll(loaded);
        // Later: the app data file can only be saved once its loading is finished.
        Platform.runLater(() -> {
            File folder = EvaluationFolders.getActiveFolder();
            if(folder != null) migrateAppNotes(folder);
        });
    }
    
    /**
     * Notes taken before the notes were stored with the evaluations: those about a copy of this folder are moved into it.
     * All or nothing: the screenshots are copied first, then the notes are written in the folder, and only then removed from the app data.
     * The old screenshots are left in the app data (never deleted automatically).
     */
    private static void migrateAppNotes(File folder){
        List<TeacherNote> candidates = notes.stream().filter(note -> note.getFolder() == null && folder.equals(note.getCopyFolder())).toList();
        if(candidates.isEmpty()) return;
        
        ArrayList<TeacherNote> moved = new ArrayList<>();
        for(TeacherNote note : candidates){
            // Already moved, but the app data file was not saved after
            if(notes.stream().anyMatch(other -> other != note && folder.equals(other.getFolder()) && other.getId().equals(note.getId()))){
                notes.remove(note);
                continue;
            }
            try{
                File oldImage = getImageFile(note);
                if(oldImage != null && oldImage.exists()){
                    File imagesFolder = getImagesFolder(folder);
                    imagesFolder.mkdirs();
                    Files.copy(oldImage.toPath(), new File(imagesFolder, note.getImage()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                moved.add(note);
            }catch(Exception e){
                Log.eNotified(e, "Unable to move a note into its evaluation folder: it stays in the app data");
            }
        }
        if(moved.isEmpty()) return;
        
        for(TeacherNote note : moved){
            thumbnails.remove(getImageKey(note));
            note.setFolder(folder);
        }
        if(!EvaluationFolders.saveNow(FOLDER_PART)){
            for(TeacherNote note : moved) note.setFolder(null); // Not written in the folder: they stay in the app data
            return;
        }
        TeacherNotesData.saveNow();
        revision.set(revision.get() + 1);
    }
    
    // EDITION
    
    private static void requestSave(TeacherNote note){
        if(note.getFolder() == null) TeacherNotesData.requestSave();
        else if(note.getFolder().equals(EvaluationFolders.getActiveFolder())) EvaluationFolders.requestSave(FOLDER_PART);
    }
    public static void add(TeacherNote note){
        notes.addFirst(note);
        requestSave(note);
    }
    public static void setText(TeacherNote note, String text){
        if(note.getText().equals(text)) return;
        note.setText(text);
        revision.set(revision.get() + 1);
        requestSave(note);
    }
    public static void delete(TeacherNote note){
        notes.remove(note);
        if(note.getImage() != null){
            thumbnails.remove(getImageKey(note));
            File image = getImageFile(note);
            if(image != null && image.exists() && !image.delete()) Log.w("Unable to delete the note image " + image);
        }
        requestSave(note);
    }
    
    // IMAGES
    
    // Screenshots of the notes of an evaluation folder, or of the app data (folder = null).
    public static File getImagesFolder(File folder){
        if(folder == null) return new File(Main.dataFolder, "notes");
        return new File(EvaluationFolders.getDataDir(folder), "notes");
    }
    public static File getImageFile(TeacherNote note){
        return note.getImage() == null ? null : new File(getImagesFolder(note.getFolder()), note.getImage());
    }
    private static String getImageKey(TeacherNote note){
        File file = getImageFile(note);
        return file == null ? "" : file.getAbsolutePath();
    }
    // Image scaled to the width of the notes panel, loaded in background.
    public static Image getThumbnail(TeacherNote note){
        File file = getImageFile(note);
        if(file == null || !file.exists()) return null;
        return thumbnails.computeIfAbsent(file.getAbsolutePath(), name -> new Image(file.toURI().toString(), 600, 0, true, true, true));
    }
    public static Image getFullImage(TeacherNote note){
        File file = getImageFile(note);
        if(file == null || !file.exists()) return null;
        return new Image(file.toURI().toString());
    }
    // Returns the file name, or null if it could not be written.
    private static String writeImage(File folder, String id, Image image){
        File imagesFolder = getImagesFolder(folder);
        File file = new File(imagesFolder, id + ".png");
        try{
            imagesFolder.mkdirs();
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            return file.getName();
        }catch(IOException e){
            Log.eNotified(e, "Unable to save the note screenshot");
            return null;
        }
    }
    
    // CAPTURE

    public static void captureTextNote(){
        Platform.runLater(() -> QuickNoteDialog.show(null, getContextPage()));
    }

    public static void captureScreenshotNote(){
        if(!MainWindow.mainScreen.hasDocument(false) || MainWindow.mainScreen.isEditPagesMode()){
            MainWindow.footerBar.showToast(Color.web("#6a1b1b"), Color.WHITE, TR.tr("notes.screenshot.noDocument"));
            return;
        }
        RegionCapture.arm((page, image) -> QuickNoteDialog.show(image, page));
    }

    // Creates the note, with the copy, page and exercise currently open. The image can be null.
    static void create(String text, Image image, PageRenderer page){
        String file = null;
        String exercise = null;
        if(MainWindow.mainScreen.hasDocument(false)){
            file = MainWindow.mainScreen.document.getFile().getAbsolutePath();
            if(MainWindow.footerBar.getExerciseCount() > 0) exercise = MainWindow.footerBar.getSelectedExerciseKey();
        }
        TeacherNote note = new TeacherNote(text, null, file, page == null ? -1 : page.getPage(), exercise);
        // With a document open, the note is stored with its evaluation.
        if(file != null) note.setFolder(EvaluationFolders.getActiveFolder());
        if(image != null) note.setImage(writeImage(note.getFolder(), note.getId(), image));
        add(note);
        MainWindow.footerBar.showToast(Color.web("#1b5e20"), Color.WHITE, TR.tr("notes.saved"));
    }

    static PageRenderer getContextPage(){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        return MainWindow.mainScreen.document.getCenterVisiblePage();
    }

    // NAVIGATION

    // Opens the copy of the note at its page.
    public static void goTo(TeacherNote note){
        if(note.getFile() == null) return;
        EvaluationComments.openCopyAt(new File(note.getFile()), note.getPage());
    }
}
