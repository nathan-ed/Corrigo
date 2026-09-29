/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.notes;

import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.FooterBar;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.scene.shape.Rectangle;

import java.util.function.BiConsumer;

/**
 * Next drag on a page selects a region of it, which is captured as an image (as displayed, with the annotations).
 * A click without dragging captures the visible part of the page. Escape cancels.
 * The pixels are read from the screen: Node.snapshot forces a layout pass of the whole window,
 * which never ends with the side bars split pane (infinite loop in SplitPaneSkin.distributeTo).
 */
final class RegionCapture {

    private static final double MIN_SIZE = 8;
    private static RegionCapture armed;

    private final BiConsumer<PageRenderer, Image> onCaptured;
    private final Scene scene;
    private final EventHandler<MouseEvent> mouseFilter = this::onMouse;
    private final EventHandler<KeyEvent> keyFilter = this::onKey;

    private PageRenderer page;
    private Point2D start;
    private Rectangle selection;

    private RegionCapture(BiConsumer<PageRenderer, Image> onCaptured){
        this.onCaptured = onCaptured;
        this.scene = MainWindow.mainScreen.getScene();
    }

    static void arm(BiConsumer<PageRenderer, Image> onCaptured){
        if(armed != null) armed.disarm();
        armed = new RegionCapture(onCaptured);
        MainWindow.mainScreen.addEventFilter(MouseEvent.ANY, armed.mouseFilter);
        armed.scene.addEventFilter(KeyEvent.KEY_PRESSED, armed.keyFilter);
        MainWindow.footerBar.showToast(Color.web("#424242"), Color.WHITE, FooterBar.ToastDuration.LONG, TR.tr("notes.screenshot.hint"));
    }

    private void disarm(){
        MainWindow.mainScreen.removeEventFilter(MouseEvent.ANY, mouseFilter);
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        removeSelection();
        if(armed == this) armed = null;
    }

    private void onKey(KeyEvent e){
        if(e.getCode() != KeyCode.ESCAPE) return;
        e.consume();
        disarm();
    }

    private void onMouse(MouseEvent e){
        if(e.getEventType() == MouseEvent.MOUSE_MOVED || e.getEventType() == MouseEvent.MOUSE_ENTERED
                || e.getEventType() == MouseEvent.MOUSE_EXITED || e.getEventType() == MouseEvent.MOUSE_ENTERED_TARGET
                || e.getEventType() == MouseEvent.MOUSE_EXITED_TARGET) return;
        if(e.getButton() != MouseButton.PRIMARY && e.getEventType() != MouseEvent.MOUSE_DRAGGED) return;
        e.consume(); // The elements and pages must not react (selection, moving...)

        if(e.getEventType() == MouseEvent.MOUSE_PRESSED){
            removeSelection();
            page = findPage(e.getSceneX(), e.getSceneY());
            if(page == null) return;
            start = clamp(page.sceneToLocal(e.getSceneX(), e.getSceneY()));
            selection = new Rectangle(start.getX(), start.getY(), 0, 0);
            selection.setFill(Color.web("#1565c0", .12));
            selection.setStroke(Color.web("#1565c0"));
            selection.setStrokeWidth(1.5);
            selection.getStrokeDashArray().setAll(6d, 4d);
            selection.setMouseTransparent(true);
            selection.setManaged(false);
            page.getChildren().add(selection);
        }else if(e.getEventType() == MouseEvent.MOUSE_DRAGGED){
            if(selection == null) return;
            Point2D current = clamp(page.sceneToLocal(e.getSceneX(), e.getSceneY()));
            selection.setX(Math.min(start.getX(), current.getX()));
            selection.setY(Math.min(start.getY(), current.getY()));
            selection.setWidth(Math.abs(current.getX() - start.getX()));
            selection.setHeight(Math.abs(current.getY() - start.getY()));
        }else if(e.getEventType() == MouseEvent.MOUSE_RELEASED){
            if(selection == null) return;
            PageRenderer page = this.page;
            Bounds region = selection.getWidth() < MIN_SIZE || selection.getHeight() < MIN_SIZE
                    ? page.localToScreen(page.getLayoutBounds()) // Simple click: whole page
                    : page.localToScreen(selection.getBoundsInParent());
            disarm(); // Removes the selection rectangle before the capture
            Rectangle2D visible = intersect(region, MainWindow.mainScreen.localToScreen(MainWindow.mainScreen.getLayoutBounds()));
            if(visible == null) return;
            // The capture is done once the window is rendered without the selection rectangle (and not while the mouse event is dispatched)
            afterRender(() -> {
                Image image = new Robot().getScreenCapture(null, visible, false);
                Platform.runLater(() -> onCaptured.accept(page, image)); // showAndWait is not allowed during an animation
            });
        }
    }
    
    private static Rectangle2D intersect(Bounds a, Bounds b){
        if(a == null || b == null) return null;
        double minX = Math.max(a.getMinX(), b.getMinX()), minY = Math.max(a.getMinY(), b.getMinY());
        double maxX = Math.min(a.getMaxX(), b.getMaxX()), maxY = Math.min(a.getMaxY(), b.getMaxY());
        if(maxX - minX < 1 || maxY - minY < 1) return null;
        return new Rectangle2D(Math.floor(minX), Math.floor(minY), Math.ceil(maxX - minX), Math.ceil(maxY - minY));
    }
    
    // Runs the action after a few frames and at least 150 ms: the render thread can be late on the FX thread.
    private static void afterRender(Runnable action){
        long start = System.nanoTime();
        new AnimationTimer(){
            private int frames;
            @Override public void handle(long now){
                if(++frames < 3 || now - start < 150_000_000L) return;
                stop();
                action.run();
            }
        }.start();
    }

    private void removeSelection(){
        if(selection != null && page != null) page.getChildren().remove(selection);
        selection = null;
    }

    private Point2D clamp(Point2D point){
        return new Point2D(Math.clamp(point.getX(), 0, page.getWidth()), Math.clamp(point.getY(), 0, page.getHeight()));
    }

    private static PageRenderer findPage(double sceneX, double sceneY){
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            if(page.isVisible() && page.getLayoutBounds().contains(page.sceneToLocal(sceneX, sceneY))) return page;
        }
        return null;
    }
}
