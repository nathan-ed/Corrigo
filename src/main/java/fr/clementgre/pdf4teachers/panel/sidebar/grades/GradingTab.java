/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

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
