/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.notes;

import fr.clementgre.pdf4teachers.panel.sidebar.SideTab;
import fr.clementgre.pdf4teachers.utils.svg.SVGPathIcons;

// Side bar tab of the teacher's notes (see NotesPanel).
public class NotesTab extends SideTab {
    
    public NotesTab(){
        super("notes", SVGPathIcons.BOOK, 26, 1);
        setContent(new NotesPanel());
    }
}
