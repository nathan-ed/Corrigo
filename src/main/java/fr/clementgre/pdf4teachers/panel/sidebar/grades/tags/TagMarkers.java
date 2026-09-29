/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation.ExerciseLocator;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Kind;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Placement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.EvaluationTags.Tag;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The methods and mistakes of the open copy shown on its pages: a small pill at the spot where each one was put
 * (or, for those added from the grading panel, stacked in the margin of the exercise page).
 * Only on screen: the pills are neither saved in the edition nor exported.
 */
public final class TagMarkers {

    private TagMarkers(){
    }

    private static boolean scheduled;

    public static boolean isShown(){
        return MainWindow.userData == null || !MainWindow.userData.hideTagMarkers;
    }
    public static void setShown(boolean shown){
        MainWindow.userData.hideTagMarkers = !shown;
        update();
    }

    // Updates the pills later (several changes at once are applied once).
    public static void update(){
        if(scheduled) return;
        scheduled = true;
        Platform.runLater(() -> {
            scheduled = false;
            updateNow();
        });
    }

    private static boolean zoomFollowed;
    private static void updateNow(){
        if(!zoomFollowed){ // The dots are grabbed at the same size on the screen, whatever the zoom
            zoomFollowed = true;
            MainWindow.mainScreen.zoomProperty().addListener((o, oldValue, newValue) -> update());
        }
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        List<PageRenderer> pages = MainWindow.mainScreen.document.getPages();
        for(PageRenderer page : pages) page.getChildren().removeIf(node -> node instanceof Layer);
        String copy = ExerciseTags.getOpenCopy();
        if(copy == null || !isShown() || MainWindow.mainScreen.isEditPagesMode()) return;

        EvaluationTags data = ExerciseTags.getData();
        ArrayList<ArrayList<Pill>> byPage = new ArrayList<>();
        for(int i = 0; i < pages.size(); i++) byPage.add(new ArrayList<>());
        for(Tag tag : data.getAllTags()){
            for(EvaluationTags.Use use : data.getUses(tag)){
                if(!use.copy().equals(copy)) continue;
                int page = use.placement().page();
                if(page >= 0 && page < pages.size()) byPage.get(page).add(new Pill(tag, use));
            }
        }
        for(int i = 0; i < pages.size(); i++){
            if(!byPage.get(i).isEmpty()) pages.get(i).getChildren().add(new Layer(pages.get(i), byPage.get(i)));
        }
    }

    // Pills of a page: positioned in page units, so they follow the zoom as the elements do.
    private static final class Layer extends Pane {
        private final PageRenderer page;
        private final List<Pill> pills;

        Layer(PageRenderer page, List<Pill> pills){
            this.page = page;
            this.pills = pills.stream().sorted(Comparator.comparingDouble((Pill pill) -> pill.placement.y()).thenComparingDouble(pill -> pill.placement.x())).toList();
            setManaged(false);
            setPickOnBounds(false);
            for(Pill pill : this.pills){
                pill.layer = this;
                if(pill.hasSpot()) getChildren().addAll(pill.link, pill.spot, pill.spotHandle);
            }
            getChildren().addAll(this.pills);
            // Placed by layoutChildren only: moving the line while dragging must not place the pills again
            getChildren().forEach(node -> node.setManaged(false));
        }

        // The grade the points count on if it is on this page, else the first grade of the exercise on this page
        private GradeElement findGrade(String exercise, String gradePath){
            GradeElement own = page.getElements().stream()
                    .filter(element -> element instanceof GradeElement grade && grade.getPath().equals(gradePath))
                    .map(element -> (GradeElement) element).findFirst().orElse(null);
            if(own != null) return own;
            return page.getElements().stream()
                    .filter(element -> element instanceof GradeElement grade && exercise.equals(ExerciseLocator.getExercise(grade.getPath())))
                    .map(element -> (GradeElement) element)
                    .min(Comparator.comparingDouble(GradeElement::getLayoutY))
                    .orElse(null);
        }

        // Page units for one pixel of the screen
        private double getPixel(){
            double scale = page.localToScene(1, 0).getX() - page.localToScene(0, 0).getX();
            Node root = page.getScene() == null ? null : page.getScene().getRoot();
            if(root != null) scale *= root.getLocalToSceneTransform().getMxx();
            return scale <= 0 ? 1 : 1 / scale;
        }

        @Override protected void layoutChildren(){
            double pixel = getPixel();
            for(Pill pill : pills) pill.setSpotSizes(pixel);
            // The pills moved by the teacher stay where they were put; the others avoid them and each other (moved down)
            ArrayList<double[]> placed = new ArrayList<>(); // x, y, width, height
            List<Pill> ordered = new ArrayList<>(pills.stream().filter(pill -> pill.placement.hasLabelPosition()).toList());
            ordered.addAll(pills.stream().filter(pill -> !pill.placement.hasLabelPosition()).toList());
            for(Pill pill : ordered){
                if(pill.dragging){ // Where the mouse moved it
                    placed.add(new double[]{pill.getLayoutX(), pill.getLayoutY(), pill.getWidth() * pill.labelScale.getX(), pill.getHeight() * pill.labelScale.getY()});
                    continue;
                }
                pill.autosize();
                double width = pill.getWidth() * pill.labelScale.getX(), height = pill.getHeight() * pill.labelScale.getY();
                double x, y;
                if(pill.placement.hasLabelPosition()){
                    x = page.fromGridX(pill.placement.labelX());
                    y = page.fromGridY(pill.placement.labelY());
                }else if(!pill.hasSpot()){ // Added from the panel: under the grade of the exercise, or in the margin
                    GradeElement grade = findGrade(pill.exercise, pill.grade);
                    if(grade != null){
                        x = grade.getLayoutX();
                        y = grade.getLayoutY() + grade.getBoundsHeight() + 2;
                    }else{
                        x = page.getWidth() * .004;
                        y = page.fromGridY(pill.placement.y());
                    }
                }else{ // Put at a spot: in the left margin at its height (a dot on the spot, linked to the pill)
                    x = page.getWidth() * .006;
                    y = page.fromGridY(pill.placement.y()) - height / 2;
                }
                x = Math.clamp(x, 0, Math.max(0, page.getWidth() - width));
                y = Math.clamp(y, 0, Math.max(0, page.getHeight() - height));
                if(!pill.placement.hasLabelPosition()){
                    boolean moved = true;
                    while(moved){
                        moved = false;
                        for(double[] other : placed){
                            if(x < other[0] + other[2] && x + width > other[0] && y < other[1] + other[3] && y + height > other[1]){
                                y = other[1] + other[3] + 1;
                                moved = true;
                            }
                        }
                    }
                }
                pill.relocate(x, y);
                placed.add(new double[]{x, y, width, height});
                if(pill.hasSpot()) pill.placeSpot(page.fromGridX(pill.placement.x()), page.fromGridY(pill.placement.y()));
            }
        }
    }

    private static final class Pill extends HBox {
        static final double DOT_RADIUS = 2.2;
        private static final double DRAG_THRESHOLD = 2;
        private final Tag tag;
        private final Placement placement;
        private final String exercise;
        private final String useId; // The occurrence it shows
        private final String grade; // Path of the grade its points count on
        private Layer layer;
        // Where it was put (a larger invisible handle to drag it), and the line from the pill to it
        private final Circle spot = new Circle(3);
        private final Circle spotHandle = new Circle(8, Color.TRANSPARENT);
        private double spotRadius = 3;
        private static final double FONT_SIZE = 7.5;
        private final javafx.scene.transform.Scale labelScale = new javafx.scene.transform.Scale(1, 1, 0, 0);
        // Shown under the mouse only: it would cross the writing of the line
        private final javafx.scene.shape.Line link = new javafx.scene.shape.Line();
        private boolean hovered;

        Pill(Tag tag, EvaluationTags.Use use){
            super(3);
            this.tag = tag;
            this.placement = use.placement();
            this.exercise = use.exercise();
            this.useId = use.id();
            this.grade = use.grade();
            String color = TagPicker.getColor(tag.getKind());
            Circle dot = new Circle(DOT_RADIUS, Color.WHITE);
            // Whole: the pill is as long as the name (and its points)
            String pointsLabel = tag.getPointsLabel(use.exercise(), MainWindow.gradesDigFormat);
            Label name = new Label(tag.getName() + (pointsLabel.isEmpty() ? "" : "  " + pointsLabel));
            name.setMinWidth(Region.USE_PREF_SIZE);
            name.setStyle("-fx-text-fill: white; -fx-font-size: " + FONT_SIZE + "; -fx-font-weight: bold;");
            getTransforms().add(labelScale);
            getChildren().addAll(dot, name);
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(1, 5, 1, 2));
            setStyle("-fx-background-color: " + color + "; -fx-background-radius: 8;");
            spot.setFill(Color.web(color));
            spot.setStroke(Color.WHITE);
            spot.setStrokeWidth(.8);
            spot.setMouseTransparent(true);
            link.setStroke(Color.web(color));
            link.setStrokeWidth(.7);
            link.getStrokeDashArray().setAll(2.0, 2.0);
            link.setMouseTransparent(true);
            link.setVisible(false);
            // Light, so that the copy stays readable; opaque under the mouse (on the pill or on its dot)
            setOpacity(.8);
            spot.setOpacity(.8);
            hoverProperty().addListener((o, oldValue, newValue) -> updateHover());
            spotHandle.hoverProperty().addListener((o, oldValue, newValue) -> updateHover());

            setCursor(Cursor.OPEN_HAND);
            spotHandle.setCursor(Cursor.OPEN_HAND);
            setupDrag(this, false);
            setupDrag(spotHandle, true);

            // One label per line, in its own font size: not the one of the page the pill is on
            Tooltip tooltip = new Tooltip();
            Label kindLine = new Label(TR.tr(tag.getKind() == Kind.METHOD ? "tags.kind.method" : "tags.kind.mistake") + " · " + exercise);
            Label nameLine = new Label(tag.getName());
            nameLine.setStyle("-fx-font-weight: bold;");
            Label hintLine = new Label(TR.tr("tags.marker.tooltip"));
            hintLine.setStyle("-fx-opacity: .8;");
            String written = TagEditFiles.describe(tag, use.exercise(), MainWindow.gradesDigFormat);
            Label writtenLine = new Label(written.isEmpty() ? "" : TR.tr("tags.chip.written", written));
            writtenLine.setManaged(!written.isEmpty());
            writtenLine.setVisible(!written.isEmpty());
            javafx.scene.layout.VBox lines = new javafx.scene.layout.VBox(2, kindLine, nameLine, writtenLine, hintLine);
            for(Label line : new Label[]{kindLine, nameLine, writtenLine, hintLine}){
                line.setMinHeight(Region.USE_PREF_SIZE);
                line.setStyle(line.getStyle() + "-fx-font-size: 12px; -fx-text-fill: white;");
            }
            tooltip.setGraphic(lines);
            tooltip.setStyle("-fx-font-size: 12px;");
            tooltip.setShowDelay(Duration.millis(300));
            Tooltip.install(this, tooltip);
            Tooltip.install(spotHandle, tooltip);
            setOnContextMenuRequested(this::showMenu);
            spotHandle.setOnContextMenuRequested(this::showMenu);
        }

        // pixel: page units for one pixel of the screen. The dot is at least 3 px in radius, grabbed 12 px around it.
        void setSpotSizes(double pixel){
            spotRadius = Math.max(3, 3 * pixel);
            spot.setRadius(hovered ? spotRadius * 1.5 : spotRadius);
            spot.setStrokeWidth(Math.max(.8, 1.2 * pixel));
            spotHandle.setRadius(Math.max(8, 12 * pixel));
            // The name stays readable (10 px at least) when zoomed out
            double scale = Math.max(1, 10 * pixel / FONT_SIZE);
            labelScale.setX(scale);
            labelScale.setY(scale);
        }

        boolean hasSpot(){
            return !ExerciseTags.isExerciseSpot(placement);
        }

        private void updateHover(){
            hovered = isHover() || spotHandle.isHover();
            setOpacity(hovered ? 1 : .8);
            spot.setRadius(hovered ? spotRadius * 1.5 : spotRadius);
            updateLink();
        }
        // The line is useful when the dot is away from the pill
        private void updateLink(){
            Bounds pill = getBoundsInParent();
            double spotX = spot.getCenterX(), spotY = spot.getCenterY();
            boolean away = spotX > pill.getMaxX() + 4 || spotX < pill.getMinX() - 4 || spotY > pill.getMaxY() + 4 || spotY < pill.getMinY() - 4;
            link.setVisible(hasSpot() && away && (hovered || dragging));
            link.setStartX(Math.clamp(spotX, pill.getMinX(), pill.getMaxX()));
            link.setStartY(Math.clamp(spotY, pill.getMinY(), pill.getMaxY()));
            link.setEndX(spotX);
            link.setEndY(spotY);
        }
        void placeSpot(double x, double y){
            spot.setCenterX(x);
            spot.setCenterY(y);
            spotHandle.setCenterX(x);
            spotHandle.setCenterY(y);
            updateLink();
        }

        // DRAG: the pill moves alone (its position is kept); the dot moves the spot. Saved when released.
        private boolean dragging;
        private double pressX, pressY, startX, startY;
        private void setupDrag(Node node, boolean isSpot){
            node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                if(e.getButton() != MouseButton.PRIMARY){
                    e.consume(); // The right click is for the menu of the pill, not for the page
                    return;
                }
                Point2D point = layer.sceneToLocal(e.getSceneX(), e.getSceneY());
                pressX = point.getX();
                pressY = point.getY();
                startX = isSpot ? spot.getCenterX() : getLayoutX();
                startY = isSpot ? spot.getCenterY() : getLayoutY();
                dragging = false;
                e.consume(); // Not a selection or a scroll of the page
            });
            node.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
                if(!e.isPrimaryButtonDown()) return;
                e.consume();
                Point2D point = layer.sceneToLocal(e.getSceneX(), e.getSceneY());
                double dx = point.getX() - pressX, dy = point.getY() - pressY;
                if(!dragging && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
                dragging = true;
                node.setCursor(Cursor.CLOSED_HAND);
                if(isSpot){
                    placeSpot(Math.clamp(startX + dx, 0, layer.page.getWidth()), Math.clamp(startY + dy, 0, layer.page.getHeight()));
                }else{
                    relocate(Math.clamp(startX + dx, 0, layer.page.getWidth() - getWidth() * labelScale.getX()),
                            Math.clamp(startY + dy, 0, layer.page.getHeight() - getHeight() * labelScale.getY()));
                    updateLink();
                }
            });
            node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                if(e.getButton() != MouseButton.PRIMARY) return;
                e.consume();
                node.setCursor(Cursor.OPEN_HAND);
                if(!dragging) return;
                dragging = false;
                String copy = ExerciseTags.getOpenCopy();
                if(copy == null) return;
                PageRenderer page = layer.page;
                Placement moved = isSpot
                        ? placement.withSpot(page.toGridX(spot.getCenterX()), page.toGridY(spot.getCenterY()))
                        : placement.withLabel(page.toGridX(getLayoutX()), page.toGridY(getLayoutY()));
                ExerciseTags.moveOccurrence(copy, useId, moved);
            });
            node.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        }

        private void showMenu(ContextMenuEvent e){
            e.consume();
            String copy = ExerciseTags.getOpenCopy();
            if(copy == null) return;
            MenuItem remove = new MenuItem(TR.tr("tags.marker.remove"));
            remove.setOnAction(a -> ExerciseTags.removeOccurrence(copy, useId));
            ContextMenu menu = new ContextMenu(remove);
            if(placement.hasLabelPosition()){ // Back in the margin
                MenuItem reset = new MenuItem(TR.tr("tags.marker.resetPosition"));
                reset.setOnAction(a -> ExerciseTags.moveOccurrence(copy, useId, placement.withLabel(Double.NaN, Double.NaN)));
                menu.getItems().add(reset);
            }
            menu.getItems().add(new SeparatorMenuItem());
            menu.getItems().addAll(ExerciseTagsCard.headed(TR.tr("tags.gallery.changeTo"), ExerciseTagsCard.buildTagChoices(getScene().getWindow(), exercise, tag.getKind(),
                    other -> other != tag, other -> ExerciseTags.changeOccurrence(copy, useId, other))));
            MenuItem hide = new MenuItem(TR.tr("tags.marker.hideAll"));
            hide.setOnAction(a -> setShown(false));
            menu.getItems().addAll(new SeparatorMenuItem(), hide);
            menu.show(this, e.getScreenX(), e.getScreenY());
        }
    }
}
