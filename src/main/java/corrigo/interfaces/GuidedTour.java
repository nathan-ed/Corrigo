/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.interfaces;

import corrigo.panel.sidebar.grades.NewEvaluationWizard;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.SideTab;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

import java.util.List;
import java.util.function.Supplier;

/**
 * The tour of the application at its first opening (and from Help): each step darkens the window except the part it
 * talks about, with a card next to it. Drawn in the window (no popup window), so that it looks the same everywhere.
 */
public final class GuidedTour {

    // A step: the part of the window shown (null: none, the card is centered), what to do before (select a tab…)
    private record Step(String key, Supplier<Node> target, Runnable before) {}

    private static final double CARD_WIDTH = 430, GAP = 14, PADDING = 6;
    private static GuidedTour current;

    private final StackPane layer;
    private final Pane overlay = new Pane();
    private final VBox card = new VBox(10);
    private final Label title = new Label(), text = new Label(), counter = new Label();
    private final Button back = new Button(), next = new Button(), skip = new Button();
    private final List<Step> steps;
    private int index;

    private GuidedTour(StackPane layer){
        this.layer = layer;
        steps = List.of(
                new Step("welcome", null, null),
                new Step("files", () -> tabHeader(MainWindow.filesTab), () -> MainWindow.filesTab.select()),
                new Step("grading", () -> tabHeader(MainWindow.gradingTab), () -> MainWindow.gradingTab.select()),
                new Step("panel", () -> MainWindow.leftBar, () -> MainWindow.gradingTab.select()),
                new Step("tags", () -> MainWindow.mainScreen, null),
                new Step("class", () -> MainWindow.leftBar, () -> MainWindow.gradingTab.select()),
                new Step("notes", () -> tabHeader(MainWindow.notesTab), () -> MainWindow.notesTab.select()),
                new Step("texts", () -> tabHeader(MainWindow.textTab), () -> MainWindow.textTab.select()),
                new Step("grades", () -> tabHeader(MainWindow.gradeTab), () -> MainWindow.gradeTab.select()),
                new Step("export", () -> MainWindow.menuBar, null),
                new Step("end", null, () -> MainWindow.filesTab.select()));

        overlay.setPickOnBounds(true); // The window cannot be used during the tour
        title.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: white;");
        title.setWrapText(true);
        text.setWrapText(true);
        text.setStyle("-fx-font-size: 13; -fx-text-fill: #e3e6ea; -fx-line-spacing: 2;");
        counter.setStyle("-fx-font-size: 11; -fx-text-fill: #9aa3ad;");
        back.setText(TR.tr("tour.back"));
        skip.setText(TR.tr("tour.skip"));
        back.setOnAction(e -> show(index - 1));
        next.setOnAction(e -> {
            if(index < steps.size() - 1) show(index + 1);
            else close();
        });
        skip.setOnAction(e -> close());
        next.setDefaultButton(true);
        next.setStyle("-fx-background-color: #2f7de1; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 4;");
        for(Button button : new Button[]{back, skip}){
            button.setStyle("-fx-background-color: transparent; -fx-text-fill: #c9d1da; -fx-padding: 6 10; -fx-border-color: #4a5058; -fx-border-radius: 4;");
        }
        for(javafx.scene.control.Control control : new javafx.scene.control.Control[]{back, next, skip, counter}) control.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, skip, spacer, counter, back, next);
        buttons.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(title, text, buttons);
        card.setPadding(new Insets(16, 18, 14, 18));
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setStyle("-fx-background-color: #23272e; -fx-background-radius: 10; -fx-border-color: #2f7de1; -fx-border-radius: 10; -fx-border-width: 1.5;");
        card.setManaged(false);

        overlay.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if(e.getCode() == KeyCode.ESCAPE) close();
            else if(e.getCode() == KeyCode.RIGHT) next.fire();
            else if(e.getCode() == KeyCode.LEFT && index > 0) back.fire();
            else return;
            e.consume();
        });
        overlay.widthProperty().addListener((o, oldValue, newValue) -> layout());
        overlay.heightProperty().addListener((o, oldValue, newValue) -> layout());
    }

    // Starts the tour on the main window (again if it was open).
    public static void start(){
        if(MainWindow.tourLayer == null) return;
        if(current != null) current.close();
        current = new GuidedTour(MainWindow.tourLayer);
        current.layer.getChildren().add(current.overlay);
        current.show(0);
    }

    private void show(int step){
        index = Math.clamp(step, 0, steps.size() - 1);
        Step s = steps.get(index);
        if(s.before() != null) s.before().run();
        title.setText(TR.tr("tour." + s.key() + ".title"));
        text.setText(TR.tr("tour." + s.key() + ".text"));
        counter.setText((index + 1) + " / " + steps.size());
        back.setVisible(index > 0);
        boolean last = index == steps.size() - 1;
        next.setText(TR.tr(last ? "tour.finish" : index == 0 ? "tour.start" : "tour.next"));
        // The last step: a button to start right away
        card.getChildren().removeIf(node -> "action".equals(node.getId()));
        if(last){
            Button start = new Button(TR.tr("menuBar.file.newEvaluation"));
            start.setId("action");
            start.setStyle("-fx-background-color: #2e9e5b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 14; -fx-background-radius: 4;");
            start.setOnAction(e -> {
                close();
                NewEvaluationWizard.show();
            });
            card.getChildren().add(2, start);
        }
        // Once the tab is selected and laid out
        javafx.application.Platform.runLater(() -> {
            layout();
            overlay.requestFocus();
        });
    }

    private void layout(){
        if(overlay.getWidth() <= 0) return;
        double width = overlay.getWidth(), height = overlay.getHeight();
        Step s = steps.get(index);
        Bounds target = null;
        Node node = s.target() == null ? null : s.target().get();
        if(node != null && node.getScene() != null && node.isVisible()){
            Bounds scene = node.localToScene(node.getBoundsInLocal());
            Bounds local = overlay.sceneToLocal(scene);
            if(local != null && local.getWidth() > 0) target = local;
        }
        // The dimmed window, with a hole around the target
        Rectangle all = new Rectangle(0, 0, width, height);
        Shape mask = all;
        Rectangle ring = null;
        if(target != null){
            Rectangle hole = new Rectangle(target.getMinX() - PADDING, target.getMinY() - PADDING,
                    target.getWidth() + 2 * PADDING, target.getHeight() + 2 * PADDING);
            hole.setArcWidth(12);
            hole.setArcHeight(12);
            mask = Shape.subtract(all, hole);
            ring = new Rectangle(hole.getX(), hole.getY(), hole.getWidth(), hole.getHeight());
            ring.setArcWidth(12);
            ring.setArcHeight(12);
            ring.setFill(Color.TRANSPARENT);
            ring.setStroke(Color.web("#4aa3ff"));
            ring.setStrokeWidth(2.5);
            ring.setMouseTransparent(true);
        }
        mask.setFill(Color.rgb(8, 10, 14, .62));
        overlay.getChildren().setAll(mask);
        if(ring != null) overlay.getChildren().add(ring);
        overlay.getChildren().add(card);

        card.applyCss();
        card.autosize();
        double cardHeight = card.prefHeight(CARD_WIDTH), x, y;
        if(target == null){
            x = (width - CARD_WIDTH) / 2;
            y = (height - cardHeight) / 2;
        }else if(target.getMaxX() + GAP + PADDING + CARD_WIDTH < width){ // Right of it
            x = target.getMaxX() + GAP + PADDING;
            y = target.getMinY();
        }else if(target.getMaxY() + GAP + cardHeight < height){ // Under it
            x = target.getMinX();
            y = target.getMaxY() + GAP + PADDING;
        }else{ // Inside it, at the bottom
            x = target.getMinX() + (target.getWidth() - CARD_WIDTH) / 2;
            y = target.getMaxY() - cardHeight - 30;
        }
        // A large target (the whole panel or document): the card near its top
        if(target != null && target.getHeight() > height * .6 && x > target.getMaxX()) y = target.getMinY() + 60;
        x = Math.clamp(x, 10, Math.max(10, width - CARD_WIDTH - 10));
        y = Math.clamp(y, 10, Math.max(10, height - cardHeight - 10));
        card.resizeRelocate(x, y, CARD_WIDTH, cardHeight);
    }

    private void close(){
        layer.getChildren().remove(overlay);
        if(current == this) current = null;
    }

    // The icon of a tab in the side bar
    private static Node tabHeader(SideTab tab){
        return tab == null ? null : tab.getGraphic();
    }
}
