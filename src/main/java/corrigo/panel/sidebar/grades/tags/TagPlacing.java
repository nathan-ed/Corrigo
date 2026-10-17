/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

/**
 * Something to write on the copy where the teacher clicks next (a mistake with its comment, a criterion tick, a
 * presentation mark): after a click on its chip, the next click on a page puts it there. Escape, or a click on the
 * same chip, cancels.
 */
public final class TagPlacing {

    // What is waiting for its spot: the key identifies the chip that armed it, the action puts it on the page
    public record Pending(String key, String name, Kind kind, Action action) {}
    public enum Kind { METHOD, MISTAKE }
    @FunctionalInterface
    public interface Action {
        void place(PageRenderer page, double x, double y);
    }

    private static final SimpleObjectProperty<Pending> pending = new SimpleObjectProperty<>();
    private static final EventHandler<MouseEvent> mouseFilter = TagPlacing::onMouse;
    private static final EventHandler<KeyEvent> keyFilter = TagPlacing::onKey;
    private static Scene scene;
    // Placed on the mouse press: its release and click are not given to the page either
    private static boolean swallowing;

    private TagPlacing(){
    }

    public static ReadOnlyObjectProperty<Pending> pendingProperty(){
        return pending;
    }
    public static boolean isArmed(){
        return pending.get() != null;
    }
    public static boolean isArmed(String key){
        return pending.get() != null && pending.get().key().equals(key);
    }

    // Waits for the click on the page. The same key again cancels.
    public static void arm(String key, String name, Kind kind, Action action){
        if(isArmed(key)){
            disarm();
            return;
        }
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        removeFilters();
        MainWindow.mainScreen.setToPlace(null); // A quick mark waiting for its place
        scene = MainWindow.mainScreen.getScene();
        MainWindow.mainScreen.addEventFilter(MouseEvent.ANY, mouseFilter);
        if(scene != null) scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        pending.set(new Pending(key, name, kind, action));
        setPagesCursor(Cursor.CROSSHAIR);
        MainWindow.footerBar.showToast(Color.web(TagPicker.getColor(kind == Kind.METHOD ? EvaluationTags.Kind.METHOD : EvaluationTags.Kind.MISTAKE)),
                Color.WHITE, FooterBar.ToastDuration.LONG, TR.tr("tags.place.hint", name));
    }

    public static void disarm(){
        if(!isArmed()) return;
        removeFilters();
        pending.set(null);
        setPagesCursor(Cursor.DEFAULT);
        MainWindow.footerBar.hideToast();
    }
    private static void removeFilters(){
        MainWindow.mainScreen.removeEventFilter(MouseEvent.ANY, mouseFilter);
        if(scene != null) scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        scene = null;
        swallowing = false;
    }

    private static void onKey(KeyEvent e){
        if(e.getCode() != KeyCode.ESCAPE || !isArmed()) return;
        e.consume();
        disarm();
    }

    private static void onMouse(MouseEvent e){
        if(e.getEventType() != MouseEvent.MOUSE_PRESSED && e.getEventType() != MouseEvent.MOUSE_RELEASED
                && e.getEventType() != MouseEvent.MOUSE_CLICKED && e.getEventType() != MouseEvent.MOUSE_DRAGGED) return;
        if(e.getButton() != MouseButton.PRIMARY) return; // Scrolling, right-click menu: still waiting
        if(swallowing){ // The end of the click that placed it
            e.consume();
            if(e.getEventType() == MouseEvent.MOUSE_CLICKED || e.getEventType() == MouseEvent.MOUSE_RELEASED && !e.isStillSincePress()) removeFilters();
            return;
        }
        if(e.getEventType() != MouseEvent.MOUSE_PRESSED) return;
        PageRenderer page = findPage(e.getSceneX(), e.getSceneY());
        if(page == null) return; // Between the pages
        e.consume(); // The elements and the page must not react (selection, new text...)
        Pending placing = pending.get();
        var point = page.sceneToLocal(e.getSceneX(), e.getSceneY());
        pending.set(null);
        setPagesCursor(Cursor.DEFAULT);
        MainWindow.footerBar.hideToast();
        swallowing = true;
        placing.action().place(page, point.getX(), point.getY());
    }

    private static void setPagesCursor(Cursor cursor){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()) page.setCursor(cursor);
    }

    private static PageRenderer findPage(double sceneX, double sceneY){
        if(!MainWindow.mainScreen.hasDocument(false)) return null;
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            if(page.isVisible() && page.getLayoutBounds().contains(page.sceneToLocal(sceneX, sceneY))) return page;
        }
        return null;
    }
}
