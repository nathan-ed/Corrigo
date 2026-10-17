/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import corrigo.panel.sidebar.grades.tags.TagPlacing;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GraphicElement;
import fr.clementgre.pdf4teachers.document.editions.elements.VectorElement;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import fr.clementgre.pdf4teachers.panel.sidebar.paint.lists.VectorData;
import fr.clementgre.pdf4teachers.utils.svg.DefaultFavoriteVectors;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick marks on the copy, in the grading panel: a line, a tick, a cross, a circle. They are the shapes of the paint
 * tab (DefaultFavoriteVectors): a click on one, then a click (default size) or a drag (its size) on the copy.
 * The same button again, or Escape, cancels.
 */
public class QuickMarksBar extends HBox {

    // clickWidth/Height: size put by a simple click (grid units): a line to cross out a short formula, a tick and a
    // cross as large as handwriting, an oval around a word
    private record Mark(String symbol, String tooltipKey, VectorData data, int clickWidth, int clickHeight) {}
    private static final List<Mark> MARKS = List.of(
            new Mark("╱", "quickMarks.line", DefaultFavoriteVectors.LINE, 16000, 1),
            new Mark("✓", "quickMarks.check", DefaultFavoriteVectors.CHECK, 6000, 4474),
            new Mark("✗", "quickMarks.cross", DefaultFavoriteVectors.CROSS, 5000, 5000),
            new Mark("◯", "quickMarks.circle", oval(), 16000, 7000));

    // The circle of the paint tab, stretched as dragged: an oval around a word or a line
    private static VectorData oval(){
        VectorData circle = DefaultFavoriteVectors.CIRCLE;
        return new VectorData(circle.getWidth(), circle.getHeight(), GraphicElement.RepeatMode.STRETCH, GraphicElement.ResizeMode.CORNERS,
                false, circle.getFill(), circle.getStroke(), circle.getStrokeWidth(), circle.getPath(), false, false, 0, 0, 0, null);
    }

    private final String border, text, accent;
    private final List<Label> buttons = new ArrayList<>();
    private final EventHandler<KeyEvent> escapeFilter = e -> {
        if(e.getCode() == KeyCode.ESCAPE && getArmed() != null){
            e.consume();
            MainWindow.mainScreen.setToPlace(null);
        }
    };
    private Scene filteredScene;

    public QuickMarksBar(String border, String text, String muted, String accent){
        super(6);
        this.border = border;
        this.text = text;
        this.accent = accent;
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2, 10, 2, 10));
        Label title = new Label(TR.tr("quickMarks.title"));
        title.setStyle("-fx-font-size: 11; -fx-text-fill: " + muted + ";");
        getChildren().add(title);
        for(Mark mark : MARKS){
            Label button = new Label(mark.symbol());
            button.setMinWidth(30);
            button.setAlignment(Pos.CENTER);
            button.setCursor(Cursor.HAND);
            button.setTooltip(new Tooltip(TR.tr(mark.tooltipKey()) + "\n" + TR.tr("quickMarks.tooltip")));
            button.setOnMouseClicked(e -> {
                if(e.getButton() != MouseButton.PRIMARY || !MainWindow.mainScreen.hasDocument(false)) return;
                if(getArmed() == mark){
                    MainWindow.mainScreen.setToPlace(null);
                    return;
                }
                TagPlacing.disarm(); // One thing waits for the click on the copy at a time
                mark.data().setAsToPlaceElement(false);
                MainWindow.footerBar.showToast(Color.web("#424242"), Color.WHITE, FooterBar.ToastDuration.MEDIUM, TR.tr("quickMarks.hint"));
            });
            buttons.add(button);
            getChildren().add(button);
        }
        MainWindow.mainScreen.toPlaceProperty().addListener((o, oldValue, newValue) -> {
            updateStyle();
            setElementsTransparent(getArmed() != null);
            if(newValue == null) MainWindow.footerBar.hideToast();
            updateEscapeFilter();
        });
        updateStyle();
    }

    // A mark put with a click (not dragged): the size of its shape, centered on the click. Returns false if it is not a quick mark.
    public static boolean setClickedSize(GraphicElement element){
        if(!(element instanceof VectorElement vector) || element.getPage() == null) return false;
        Mark mark = MARKS.stream().filter(m -> m.data().getPath().equals(vector.getPath())).findFirst().orElse(null);
        if(mark == null) return false;
        double pageWidth = element.getPage().getWidth(), pageHeight = element.getPage().getHeight();
        double width = mark.clickWidth() * pageWidth / Element.GRID_WIDTH;
        double height = Math.max(1, mark.clickHeight() * pageHeight / Element.GRID_HEIGHT);
        double x = element.getRealX() * pageWidth / Element.GRID_WIDTH - width / 2;
        double y = element.getRealY() * pageHeight / Element.GRID_HEIGHT - height / 2;
        // Upright: the inversions set to follow a drag are not wanted without a drag
        vector.setInvertX(false);
        vector.setInvertY(false);
        element.checkLocation(Math.max(0, x), Math.max(0, y), width, height, false);
        return true;
    }

    // The mark waiting to be placed, or null
    private static Mark getArmed(){
        if(!(MainWindow.mainScreen.getToPlace() instanceof VectorElement element)) return null;
        return MARKS.stream().filter(mark -> mark.data().getPath().equals(element.getPath())).findFirst().orElse(null);
    }

    private void updateStyle(){
        Mark armed = getArmed();
        for(int i = 0; i < MARKS.size(); i++){
            boolean on = MARKS.get(i) == armed;
            buttons.get(i).setStyle("-fx-font-size: 15; -fx-padding: 0 6; -fx-background-radius: 6; -fx-border-radius: 6; -fx-border-color: "
                    + (on ? accent : border) + "; -fx-text-fill: " + (on ? "white" : text) + "; -fx-background-color: " + (on ? accent : "transparent") + ";");
        }
    }

    // While a mark waits, a click on the copy is for it, even on a text or a shape already there (it would take it)
    private static boolean transparent;
    private static void setElementsTransparent(boolean on){
        if(on == transparent && !on) return;
        transparent = on;
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        for(var page : MainWindow.mainScreen.document.getPages()){
            for(Element element : page.getElements()) element.setMouseTransparent(on);
        }
    }

    private void updateEscapeFilter(){
        if(filteredScene != null) filteredScene.removeEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
        filteredScene = getArmed() == null ? null : MainWindow.mainScreen.getScene();
        if(filteredScene != null) filteredScene.addEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
    }
}
