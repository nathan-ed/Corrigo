/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.panel.sidebar.SideTab;
import fr.clementgre.pdf4teachers.utils.svg.SVGPathIcons;

// Side bar tab of the grading panel (see GradingPanel).
public class GradingTab extends SideTab {
    
    public GradingTab(GradingPanel panel){
        super("grading", SVGPathIcons.LIST, 26, 1);
        setContent(panel);
        selectedProperty().addListener((o, oldValue, newValue) -> {
            if(newValue) panel.onTabSelected();
        });
    }
}
