/*
 * Copyright (c) 2021-2022. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 * Modified by Nathan, 2026.
 */

package fr.clementgre.pdf4teachers.datasaving.simpleconfigs;


import corrigo.datasaving.simpleconfigs.ExerciseCorrectionData;
import corrigo.datasaving.simpleconfigs.ScoredCommentsData;
import corrigo.datasaving.simpleconfigs.TeacherNotesData;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.datasaving.Config;
import fr.clementgre.pdf4teachers.datasaving.UserData;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;

import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class SimpleConfig {
    
    public static void registerClasses(){
        UserData.registerSimpleConfig(new FavouriteImageData());
        UserData.registerSimpleConfig(new TextElementsData());
        UserData.registerSimpleConfig(new VectorElementsData());
        UserData.registerSimpleConfig(new SkillsAssessmentData());
        UserData.registerSimpleConfig(new SystemFontsData());
        UserData.registerSimpleConfig(new ExerciseCorrectionData());
        UserData.registerSimpleConfig(new ScoredCommentsData());
        UserData.registerSimpleConfig(new TeacherNotesData());
    }
    
    private static final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SimpleConfigs saver");
        thread.setDaemon(true);
        return thread;
    });
    
    private final String filename;
    // Saving is refused until the existing file has been loaded (and applied on the FX thread), so that a save can't wipe it.
    private volatile boolean loaded;
    private ScheduledFuture<?> scheduledSave;
    private long lastSnapshotId;
    private long lastWrittenSnapshotId;
    
    public SimpleConfig(String fileName){
        this.filename = fileName;
    }
    
    protected abstract void manageLoadedData(Config config);
    protected abstract void unableToLoadConfig();
    // Called on the JavaFX thread.
    protected abstract void addDataToConfig(Config config);
    
    public final void loadData(){
        
        new Thread(() -> {
            File file = getFile();
            try{
                new File(Main.dataFolder).mkdirs();
                if(file.createNewFile()){
                    unableToLoadConfig();
                    markLoadedAfterPendingUITasks();
                    return; // File does not exist or can't create it
                }
                
                Config config = new Config(file);
                config.load();
                
                manageLoadedData(config); // Subclass cass
                
            }catch(Exception e){
                Log.eNotified(e, "Unable to load " + filename);
                backupUnreadableFile(file);
                unableToLoadConfig();
            }
            markLoadedAfterPendingUITasks();
        }, "SimpleConfigs loader").start();
    }
    // manageLoadedData usually applies the data with Platform.runLater: wait for it before accepting saves.
    private void markLoadedAfterPendingUITasks(){
        Platform.runLater(() -> loaded = true);
    }
    private void backupUnreadableFile(File file){
        try{
            if(file.exists()) Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException e){
            Log.eNotified(e, "Unable to backup " + filename);
        }
    }
    
    // Debounced asynchronous save. Can be called from any thread.
    public final synchronized void scheduleSave(){
        if(scheduledSave != null) scheduledSave.cancel(false);
        scheduledSave = saveScheduler.schedule(() -> Platform.runLater(() -> {
            Snapshot snapshot = takeSnapshot();
            if(snapshot != null) saveScheduler.execute(() -> write(snapshot));
        }), 1, TimeUnit.SECONDS);
    }
    
    // Synchronous save, must be called on the JavaFX thread.
    public final void saveData(){
        synchronized(this){
            if(scheduledSave != null) scheduledSave.cancel(false);
        }
        Snapshot snapshot = takeSnapshot();
        if(snapshot != null) write(snapshot);
    }
    
    private record Snapshot(long id, Config config) {}
    
    private Snapshot takeSnapshot(){
        if(!loaded){
            Log.w("Not saving " + filename + ": data not loaded yet.");
            return null;
        }
        try{
            Config config = new Config(getFile());
            addDataToConfig(config); // Subclass cass
            synchronized(this){
                return new Snapshot(++lastSnapshotId, config);
            }
        }catch(Exception e){
            Log.eNotified(e, "Unable to save " + filename);
            return null;
        }
    }
    
    // Writes into a temporary file then moves it, so the file is never left half written.
    // An older snapshot is never written after a newer one.
    private synchronized void write(Snapshot snapshot){
        if(snapshot.id() <= lastWrittenSnapshotId) return;
        try{
            new File(Main.dataFolder).mkdirs();
            File file = getFile();
            File tmpFile = new File(file.getPath() + ".tmp");
            snapshot.config().saveTo(tmpFile);
            try{
                Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException e){
                Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            lastWrittenSnapshotId = snapshot.id();
        }catch(Exception e){
            Log.eNotified(e, "Unable to save " + filename);
        }
    }
    
    private File getFile(){
        return new File(Main.dataFolder + filename + ".yml");
    }
    
}
