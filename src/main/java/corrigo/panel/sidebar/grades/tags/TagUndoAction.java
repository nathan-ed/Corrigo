/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UndoAction;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;

/**
 * Undo of a change of the methods and mistakes of the open copy (an occurrence added, removed, changed or moved):
 * Ctrl+Z undoes it like any other annotation of the copy. Each run swaps the two actions (undo, then redo).
 */
public class TagUndoAction extends UndoAction {

    private final String copy;
    private Runnable undo, redo;

    public TagUndoAction(UType undoType, String copy, Runnable undo, Runnable redo){
        super(undoType);
        this.copy = copy;
        this.undo = undo;
        this.redo = redo;
    }

    // Registers the undo of a change just made on the open copy (nothing for another copy)
    static void register(UType undoType, String copy, Runnable undo, Runnable redo){
        if(copy == null || !copy.equals(ExerciseTags.getOpenCopy()) || MainWindow.mainScreen.getUndoEngine() == null) return;
        MainWindow.mainScreen.registerNewAction(new TagUndoAction(undoType, copy, undo, redo));
    }

    @Override
    public boolean undoAndInvert(){
        if(!copy.equals(ExerciseTags.getOpenCopy())) return false;
        undo.run();
        Runnable done = undo;
        undo = redo;
        redo = done;
        return true;
    }

    @Override
    public String toString(){
        return "Method or mistake on " + copy;
    }
}
