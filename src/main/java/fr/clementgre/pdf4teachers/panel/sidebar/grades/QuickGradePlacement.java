/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.Comparator;
import java.util.List;

/**
 * Positions of the comments placed by the grading panel: in a column next to their grade,
 * at its left when the grade is in the right margin (the usual case), else at its right.
 * The comments are then selected, so that they can be moved right away.
 */
public class QuickGradePlacement {

    // Width of the column, in percent of the page width (TextElement max width unit)
    public static final double COLUMN_WIDTH = 30;
    private static final int MARGIN_X = (int) (Element.GRID_WIDTH * .015);
    private static final int GAP_Y = (int) (Element.GRID_HEIGHT * .003);
    private static final int SAME_COLUMN_TOLERANCE = (int) (Element.GRID_WIDTH * .002);

    private static final Color COMMENT_COLOR = Color.RED;
    private static final Font COMMENT_FONT = FontUtils.getFont("Open Sans", false, false, 16);
    
    public record Spot(PageRenderer page, int x, int y){}

    private QuickGradePlacement(){
    }

    // Where the next comment of this grade goes: below the comments already in its column.
    public static Spot nextSpot(GradeElement anchor, int fallbackPageIndex){
        PageRenderer page = anchor == null ? null : anchor.getPage();
        int x, startY;
        if(page == null){
            int pageIndex = Math.clamp(fallbackPageIndex, 0, MainWindow.mainScreen.document.getPagesNumber() - 1);
            page = MainWindow.mainScreen.document.getPage(pageIndex);
            x = (int) (Element.GRID_WIDTH * .05);
            startY = (int) (Element.GRID_HEIGHT * .05);
        }else{
            int columnWidth = (int) (Element.GRID_WIDTH * COLUMN_WIDTH / 100);
            int right = anchor.getRealX() + page.toGridX(anchor.getBoundsWidth());
            if(right + MARGIN_X + columnWidth <= Element.GRID_WIDTH) x = right + MARGIN_X;
            else x = Math.max(0, anchor.getRealX() - MARGIN_X - columnWidth);
            startY = anchor.getRealY();
        }
        return new Spot(page, x, getColumnBottom(page, x, startY));
    }

    // Bottom of the texts stacked from startY at this x (texts that are touching each other).
    private static int getColumnBottom(PageRenderer page, int x, int startY){
        List<Element> column = page.getElements().stream()
                .filter(element -> element instanceof TextElement)
                .filter(element -> Math.abs(element.getRealX() - x) <= SAME_COLUMN_TOLERANCE)
                .sorted(Comparator.comparingInt(Element::getRealY))
                .toList();
        int y = startY;
        for(Element element : column){
            if(element.getRealY() + element.getRealHeight() < y) continue; // Above the column
            if(element.getRealY() > y + GAP_Y * 3) break; // Not touching: another column further down
            y = element.getRealY() + element.getRealHeight() + GAP_Y;
        }
        return y;
    }

    // Places the comment of a grade (gradePath: see TextElement.getGradeCommentPath).
    public static TextElement placeText(String text, Spot spot, String gradePath){
        TextElement element = new TextElement(spot.x(), spot.y(), spot.page().getPage(), true, text, COMMENT_COLOR, COMMENT_FONT, COLUMN_WIDTH);
        element.setGradeCommentPath(gradePath);
        spot.page().addElement(element, true, UType.ELEMENT);
        MainWindow.mainScreen.setSelected(element);
        return element;
    }
}
