/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.datasaving.evaluation;

import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Data of an evaluation, stored in the folder of its copies (the folder of the open PDF), in a hidden ".pdf4teachers" folder:
 * the comments of the evaluation by exercise, the scored comments, the notes and their screenshots.
 * Each kind of data is a Part, saved in its own YAML file. The folder is activated when a document of it is opened.
 * The data folder is only created once there is something to save.
 */
public final class EvaluationFolders {

    public static final String DATA_DIR = ".pdf4teachers";

    // A kind of data stored in the evaluation folder. All the methods are called on the JavaFX thread.
    public interface Part {
        // Name of the YAML file, without extension.
        String getFileName();
        // The folder is activated: reads its data. The config is empty if the file does not exist yet.
        void load(File folder, Config config);
        // The folder is deactivated (after it was saved): forgets its data.
        void unload(File folder);
        // Writes the data into the config.
        void write(File folder, Config config);
        // When there is nothing to save, the file is not created.
        boolean isEmpty();
    }

    private static final List<Part> parts = new ArrayList<>();
    private static final SimpleObjectProperty<File> activeFolder = new SimpleObjectProperty<>();

    private static final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Evaluation folder saver");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Part, ScheduledFuture<?>> scheduledSaves = new HashMap<>();
    // An older snapshot of a file is never written after a newer one.
    private static final Map<String, Long> lastWrittenSnapshot = new HashMap<>();
    private static long lastSnapshotId;

    private EvaluationFolders(){
    }

    // Tests only
    static void reset(){
        parts.clear();
        activeFolder.set(null);
    }
    
    public static void register(Part part){
        if(!parts.contains(part)) parts.add(part);
        File folder = activeFolder.get();
        if(folder != null) load(part, folder);
    }

    public static File getActiveFolder(){
        return activeFolder.get();
    }
    public static ReadOnlyObjectProperty<File> activeFolderProperty(){
        return activeFolder;
    }
    public static File getDataDir(File folder){
        return new File(folder, DATA_DIR);
    }
    public static File getFile(File folder, Part part){
        return new File(getDataDir(folder), part.getFileName() + ".yml");
    }

    // Activates the folder of this PDF: saves and unloads the previous folder, then loads this one.
    public static void activateFor(File pdfFile){
        if(pdfFile == null) return;
        File folder = pdfFile.getAbsoluteFile().getParentFile();
        if(folder == null || folder.equals(activeFolder.get())) return;

        File previous = activeFolder.get();
        if(previous != null){
            saveAllNow();
            for(Part part : parts){
                try{
                    part.unload(previous);
                }catch(Exception e){
                    Log.eNotified(e, "Unable to unload the " + part.getFileName() + " of " + previous);
                }
            }
        }
        // Before loading: a part may save while it loads (migration), and it must save into this folder.
        activeFolder.set(folder);
        for(Part part : parts) load(part, folder);
    }
    // An error in the data of the evaluation must not prevent the copy from being opened.
    private static void load(Part part, File folder){
        try{
            part.load(folder, readConfig(folder, part));
        }catch(Exception e){
            Log.eNotified(e, "Unable to load the " + part.getFileName() + " of " + folder);
        }
    }

    // Config(File) creates the file: it is only used when the file exists.
    private static Config readConfig(File folder, Part part){
        File file = getFile(folder, part);
        if(!file.exists()) return new Config();
        try{
            Config config = new Config(file);
            config.load();
            return config;
        }catch(Exception e){
            Log.eNotified(e, "Unable to read " + file);
            backupUnreadableFile(file);
            return new Config();
        }
    }
    private static void backupUnreadableFile(File file){
        try{
            if(file.exists()) Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException e){
            Log.eNotified(e, "Unable to backup " + file);
        }
    }

    // SAVING

    // Debounced asynchronous save of a part of the active folder. Must be called on the JavaFX thread.
    public static void requestSave(Part part){
        File folder = activeFolder.get();
        if(folder == null) return;
        ScheduledFuture<?> previous = scheduledSaves.get(part);
        if(previous != null) previous.cancel(false);
        scheduledSaves.put(part, saver.schedule(() -> Platform.runLater(() -> {
            if(!folder.equals(activeFolder.get())) return; // Saved when the folder was deactivated
            Snapshot snapshot = takeSnapshot(folder, part);
            if(snapshot != null) saver.execute(() -> write(snapshot));
        }), 1, TimeUnit.SECONDS));
    }

    // Synchronous save of a part of the active folder. Must be called on the JavaFX thread.
    // Returns false if the data could not be written.
    public static boolean saveNow(Part part){
        File folder = activeFolder.get();
        if(folder == null) return false;
        ScheduledFuture<?> scheduled = scheduledSaves.remove(part);
        if(scheduled != null) scheduled.cancel(false);
        if(part.isEmpty() && !getFile(folder, part).exists()) return true;
        Snapshot snapshot = takeSnapshot(folder, part);
        return snapshot != null && write(snapshot);
    }
    
    // Synchronous save of all the parts of the active folder. Must be called on the JavaFX thread.
    public static void saveAllNow(){
        File folder = activeFolder.get();
        if(folder == null) return;
        for(Part part : parts){
            ScheduledFuture<?> scheduled = scheduledSaves.remove(part);
            if(scheduled != null) scheduled.cancel(false);
            Snapshot snapshot = takeSnapshot(folder, part);
            if(snapshot != null) write(snapshot);
        }
    }

    private record Snapshot(long id, File file, Config config) {}

    private static Snapshot takeSnapshot(File folder, Part part){
        File file = getFile(folder, part);
        if(part.isEmpty() && !file.exists()) return null;
        try{
            Config config = new Config(); // Written with saveTo
            part.write(folder, config);
            synchronized(lastWrittenSnapshot){
                return new Snapshot(++lastSnapshotId, file, config);
            }
        }catch(Exception e){
            Log.eNotified(e, "Unable to save " + file);
            return null;
        }
    }

    // Writes into a temporary file then moves it, so the file is never left half written. Returns false on error.
    private static boolean write(Snapshot snapshot){
        synchronized(lastWrittenSnapshot){
            String key = snapshot.file().getAbsolutePath();
            if(snapshot.id() <= lastWrittenSnapshot.getOrDefault(key, 0L)) return true; // A newer one was written
            try{
                snapshot.file().getParentFile().mkdirs();
                File tmpFile = new File(snapshot.file().getPath() + ".tmp");
                snapshot.config().saveTo(tmpFile);
                try{
                    Files.move(tmpFile.toPath(), snapshot.file().toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }catch(AtomicMoveNotSupportedException e){
                    Files.move(tmpFile.toPath(), snapshot.file().toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                lastWrittenSnapshot.put(key, snapshot.id());
                return true;
            }catch(Exception e){
                Log.eNotified(e, "Unable to save " + snapshot.file());
                return false;
            }
        }
    }
}
