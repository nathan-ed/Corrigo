/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.notes;

import fr.clementgre.pdf4teachers.panel.sidebar.SideTab;
import fr.clementgre.pdf4teachers.utils.svg.SVGPathIcons;

// Side bar tab of the teacher's notes (see NotesPanel).
public class NotesTab extends SideTab {
    
    public NotesTab(){
        super("notes", SVGPathIcons.BOOK, 26, 1);
        setContent(new NotesPanel());
    }
}
