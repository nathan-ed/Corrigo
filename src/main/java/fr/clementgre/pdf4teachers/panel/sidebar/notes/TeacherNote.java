/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.notes;

import fr.clementgre.pdf4teachers.datasaving.Config;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A note for the teacher only (never written on the copies): a text, optionally a screenshot of a copy,
 * and where it was taken (copy, page, exercise) to go back there.
 */
public class TeacherNote {

    private final String id;
    private final long created;
    private String text;
    // Name of the PNG file in the notes images folder, or null
    private String image;
    // Context: absolute path of the copy, page index, exercise name. Each can be null / -1.
    private final String file;
    // Evaluation folder where the note is stored, or null if it is stored in the app data (taken with no document open).
    private File folder;
    private final int page;
    private final String exercise;

    public TeacherNote(String text, String image, String file, int page, String exercise){
        this(UUID.randomUUID().toString(), System.currentTimeMillis(), text, image, file, page, exercise);
    }
    private TeacherNote(String id, long created, String text, String image, String file, int page, String exercise){
        this.id = id;
        this.created = created;
        this.text = text == null ? "" : text;
        this.image = image;
        this.file = file;
        this.page = page;
        this.exercise = exercise;
    }

    // In an evaluation folder, the copy is saved relatively to it, so that the folder can be moved.
    public LinkedHashMap<String, Object> toYAML(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("created", created);
        data.put("text", text);
        if(image != null) data.put("image", image);
        if(file != null){
            File copy = new File(file);
            data.put("file", folder != null && folder.equals(copy.getParentFile()) ? copy.getName() : file);
        }
        if(page >= 0) data.put("page", page);
        if(exercise != null) data.put("exercise", exercise);
        return data;
    }
    // @param folder The evaluation folder the note is read from, or null for the app data.
    public static TeacherNote fromYAML(Map<?, ?> yaml, File folder){
        HashMap<String, Object> data = Config.castSection(yaml);
        String id = Config.getString(data, "id");
        String image = Config.getString(data, "image");
        String file = Config.getString(data, "file");
        if(!file.isEmpty() && folder != null && !new File(file).isAbsolute()) file = new File(folder, file).getAbsolutePath();
        String exercise = Config.getString(data, "exercise");
        TeacherNote note = new TeacherNote(id.isEmpty() ? UUID.randomUUID().toString() : id,
                Config.getLong(data, "created"),
                Config.getString(data, "text"),
                image.isEmpty() ? null : image,
                file.isEmpty() ? null : file,
                data.containsKey("page") ? (int) Config.getLong(data, "page") : -1,
                exercise.isEmpty() ? null : exercise);
        note.folder = folder;
        return note;
    }

    public boolean isAbout(File copy){
        return file != null && copy != null && new File(file).getAbsoluteFile().equals(copy.getAbsoluteFile());
    }
    public boolean matches(String search){
        if(search == null || search.isBlank()) return true;
        String lower = search.toLowerCase();
        return text.toLowerCase().contains(lower)
                || (file != null && new File(file).getName().toLowerCase().contains(lower))
                || (exercise != null && exercise.toLowerCase().contains(lower));
    }

    public String getId(){
        return id;
    }
    public long getCreated(){
        return created;
    }
    public String getText(){
        return text;
    }
    public void setText(String text){
        this.text = text == null ? "" : text;
    }
    public String getImage(){
        return image;
    }
    void setImage(String image){
        this.image = image;
    }
    public String getFile(){
        return file;
    }
    public int getPage(){
        return page;
    }
    public String getExercise(){
        return exercise;
    }
    public File getFolder(){
        return folder;
    }
    void setFolder(File folder){
        this.folder = folder;
    }
    // Folder of the copy of the note, or null.
    public File getCopyFolder(){
        return file == null ? null : new File(file).getAbsoluteFile().getParentFile();
    }
}
