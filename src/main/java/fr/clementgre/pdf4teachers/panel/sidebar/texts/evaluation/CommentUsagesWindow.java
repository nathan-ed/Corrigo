/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import javafx.geometry.Rectangle2D;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.control.Slider;
import javafx.beans.property.DoubleProperty;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.interfaces.windows.AlternativeWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Where a comment is written (CommentUsages), by evaluation: a preview of each copy around the comment,
 * with its name, page and exercise. A click opens the copy at this page.
 */
public class CommentUsagesWindow extends AlternativeWindow<VBox> {

    // Width of the previews: two whole pages per row by default
    private static final double MIN_WIDTH = 220, MAX_WIDTH = 1400, DEFAULT_WIDTH = 450;
    // Zoom on the comment in each preview (1: the whole page); Ctrl +/- and Ctrl + scroll
    private static final double MAX_ZOOM = 5;
    // Kept for the next window
    private static double lastWidth = DEFAULT_WIDTH, lastZoom = 1;
    private final Slider size = new Slider(MIN_WIDTH, MAX_WIDTH, lastWidth);
    private final DoubleProperty cardWidth = size.valueProperty();
    private final Slider zoom = new Slider(1, MAX_ZOOM, lastZoom);

    private final String text;
    private final Label status = new Label(TR.tr("textTab.usages.searching"));
    private final VBox results = new VBox(18);
    private int copies;
    private int evaluations;
    // Renders the previews one copy at a time, stopped when the window is closed
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Comment usages previews");
        thread.setDaemon(true);
        return thread;
    });

    public CommentUsagesWindow(String text){
        // Tall from the start: the previews arrive after the window is shown
        super(new VBox(10), StageWidth.ULTRA_LARGE, getInitialHeight(), TR.tr("textTab.usages.title"), TR.tr("textTab.usages.title"), shorten(text));
        this.text = text;
        setOnHidden(e -> renderer.shutdownNow());
    }

    private static int getInitialHeight(){
        return Main.window == null ? 800 : (int) Math.max(500, Main.window.getHeight() * 0.9);
    }
    
    private static String shorten(String text){
        String oneLine = CommentBank.normalize(text);
        return oneLine.length() > 160 ? oneLine.substring(0, 157) + "…" : oneLine;
    }

    @Override
    public void setupSubClass(){
        root.setPadding(new Insets(0, 20, 15, 20));
        status.setWrapText(true);
        size.setPrefWidth(180);
        size.valueProperty().addListener((o, oldValue, newValue) -> lastWidth = newValue.doubleValue());
        zoom.setPrefWidth(180);
        zoom.valueProperty().addListener((o, oldValue, newValue) -> lastZoom = newValue.doubleValue());
        HBox controls = new HBox(8, new Label(TR.tr("textTab.usages.size")), size, new Label("    " + TR.tr("textTab.usages.zoom")), zoom);
        controls.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().addAll(status, controls, results);
        getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if(!e.isShortcutDown()) return;
            if(e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD || e.getCode() == KeyCode.EQUALS || "+".equals(e.getText())) zoomBy(1.2);
            else if(e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT || "-".equals(e.getText())) zoomBy(1 / 1.2);
            else return;
            e.consume();
        });
        getScene().addEventFilter(ScrollEvent.SCROLL, e -> {
            if(!e.isShortcutDown() || e.getDeltaY() == 0) return;
            zoomBy(e.getDeltaY() > 0 ? 1.1 : 1 / 1.1);
            e.consume();
        });

        Button close = new Button(TR.tr("actions.ok"));
        close.setOnAction(e -> close());
        setButtons(close);

        CommentUsages.search(text, this::addFolder, () -> status.setText(copies == 0 ? TR.tr("textTab.usages.none")
                : TR.tr("textTab.usages.count", String.valueOf(copies), String.valueOf(evaluations))));
    }

    private void addFolder(List<CommentUsages.Usage> usages){
        File folder = usages.getFirst().folder();
        evaluations++;
        copies += (int) usages.stream().map(CommentUsages.Usage::copy).distinct().count();

        Label name = new Label(getFolderName(folder) + "  (" + usages.size() + ")");
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        name.setTooltip(new Tooltip(folder.getAbsolutePath()));
        FlowPane cards = new FlowPane(10, 10);
        results.getChildren().add(new VBox(6, name, cards));

        // The previews of each copy are rendered together (the PDF is read once)
        LinkedHashMap<File, List<CommentUsages.Usage>> byCopy = new LinkedHashMap<>();
        LinkedHashMap<CommentUsages.Usage, StackPane> previews = new LinkedHashMap<>();
        for(CommentUsages.Usage usage : usages){
            StackPane preview = new StackPane(new ProgressIndicator());
            cards.getChildren().add(buildCard(usage, preview));
            previews.put(usage, preview);
            byCopy.computeIfAbsent(usage.copy(), k -> new ArrayList<>()).add(usage);
        }
        for(Map.Entry<File, List<CommentUsages.Usage>> copy : byCopy.entrySet()){
            renderer.submit(() -> {
                try{
                    List<PagePreview.Preview> images = PagePreview.render(copy.getKey(), copy.getValue());
                    Platform.runLater(() -> {
                        for(int i = 0; i < images.size(); i++) showPreview(previews.get(copy.getValue().get(i)), images.get(i));
                    });
                }catch(Exception e){
                    Log.e("Unable to render the preview of " + copy.getKey() + ": " + e.getMessage());
                    Platform.runLater(() -> copy.getValue().forEach(usage -> showPreview(previews.get(usage), null)));
                }
            });
        }
    }

    private void zoomBy(double factor){
        zoom.setValue(Math.clamp(zoom.getValue() * factor, 1, MAX_ZOOM));
    }
    
    private VBox buildCard(CommentUsages.Usage usage, StackPane preview){
        // A4 page, until the preview is rendered
        preview.prefWidthProperty().bind(cardWidth);
        preview.prefHeightProperty().bind(cardWidth.multiply(1.41));
        preview.setMinHeight(60);

        StringBuilder caption = new StringBuilder(usage.copy().getName().replaceFirst("(?i)\\.pdf$", ""));
        if(usage.page() >= 0) caption.append("  ·  ").append(TR.tr("notes.context.page", String.valueOf(usage.page() + 1)));
        if(usage.exercise() != null) caption.append("  ·  ").append(usage.exercise());
        File openCopy = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile().getAbsoluteFile() : null;
        if(usage.copy().equals(openCopy)) caption.append("  ").append(TR.tr("textTab.usages.thisCopy"));
        Label label = new Label(caption.toString());
        label.maxWidthProperty().bind(cardWidth);
        label.setStyle("-fx-font-size: 12;");

        VBox card = new VBox(4, preview, label);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(5));
        card.setCursor(Cursor.HAND);
        card.setStyle("-fx-border-color: rgba(128,128,128,.45); -fx-border-radius: 5; -fx-background-radius: 5;");
        Tooltip.install(card, new Tooltip(TR.tr("textTab.usages.open")));
        card.setOnMouseClicked(e -> {
            close();
            EvaluationComments.openCopyAt(usage.copy(), usage.page());
        });
        return card;
    }

    private void showPreview(StackPane preview, PagePreview.Preview page){
        if(preview == null) return;
        if(page == null){
            preview.getChildren().setAll(new Label(TR.tr("textTab.usages.noPreview")));
            return;
        }
        ImageView view = new ImageView(SwingFXUtils.toFXImage(page.image(), null));
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.fitWidthProperty().bind(cardWidth);
        // Zoomed: the part of the page around the comment, with the proportions of the page
        Runnable updateViewport = () -> view.setViewport(getViewport(page, zoom.getValue()));
        updateViewport.run();
        zoom.valueProperty().addListener((o, oldValue, newValue) -> updateViewport.run());
        preview.getChildren().setAll(view);
        preview.prefHeightProperty().unbind();
        preview.setPrefHeight(Region.USE_COMPUTED_SIZE);
    }

    static Rectangle2D getViewport(PagePreview.Preview page, double zoom){
        double width = page.image().getWidth(), height = page.image().getHeight();
        if(zoom <= 1.01) return new Rectangle2D(0, 0, width, height);
        double viewWidth = width / zoom, viewHeight = height / zoom;
        java.awt.Rectangle comment = page.comment();
        double x = Math.clamp(comment.getCenterX() - viewWidth / 2, 0, width - viewWidth);
        double y = Math.clamp(comment.getCenterY() - viewHeight / 2, 0, height - viewHeight);
        return new Rectangle2D(x, y, viewWidth, viewHeight);
    }
    
    // "Class A / Test 1": the two last folders of the path, the class and the evaluation.
    static String getFolderName(File folder){
        File parent = folder.getParentFile();
        return parent == null || parent.getName().isEmpty() ? folder.getName() : parent.getName() + " / " + folder.getName();
    }

    @Override
    public void afterShown(){
    }
}
