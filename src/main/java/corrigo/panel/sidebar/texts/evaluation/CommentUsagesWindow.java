/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.texts.evaluation;

import corrigo.panel.sidebar.notes.TeacherNotes;
import java.util.function.Consumer;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.control.Slider;
import javafx.beans.binding.Bindings;
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Previews of copies, by evaluation, zoomable around a spot: where a comment is written (CommentUsages),
 * or the copies of a method or mistake. A click opens the copy at this page.
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

    // Gives the previews to show, by folder
    public interface Source {
        void load(Consumer<List<CommentUsages.Usage>> onFolder, Runnable onDone);
    }
    /**
     * Actions on a copy without opening it: the right-click menu of its preview (null: none), and buttons next to OK.
     * An action done on a card marks it as done (dimmed, with a note) and may be undone from the same menu.
     */
    public record Actions(java.util.function.BiFunction<CommentUsages.Usage, Card, List<javafx.scene.control.MenuItem>> cardMenu, List<Button> buttons) {}
    public static final class Card {
        private final VBox node;
        private final StackPane preview;
        private final Label note = new Label();
        private Runnable undo;
        // holder: first child of the card, holding the preview (the note is shown over it)
        Card(VBox node, StackPane holder, StackPane preview){
            this.node = node;
            this.preview = preview;
            note.setWrapText(true);
            note.setStyle("-fx-font-weight: bold; -fx-font-size: 15; -fx-text-fill: white; -fx-background-color: rgba(30,30,30,.85); -fx-background-radius: 6; -fx-padding: 6 12;");
            note.setVisible(false);
            StackPane.setAlignment(note, Pos.TOP_CENTER);
            StackPane.setMargin(note, new Insets(40, 10, 0, 10));
            holder.getChildren().add(note);
        }
        // The action is done on this copy: undo restores it
        public void setDone(String text, Runnable undo){
            this.undo = undo;
            note.setText(text + "   ·   " + TR.tr("textTab.usages.undoHint"));
            note.setVisible(true);
            preview.setOpacity(.35);
        }
        // Window of the card, to own the dialogs opened from its menu
        public javafx.stage.Window getWindow(){
            return node.getScene() == null ? null : node.getScene().getWindow();
        }
        public boolean isDone(){
            return undo != null;
        }
        public void undo(){
            if(undo == null) return;
            Runnable action = undo;
            undo = null;
            action.run();
            note.setVisible(false);
            preview.setOpacity(1);
        }
    }
    private final Source source;
    private final Actions actions;
    private final String emptyText;
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

    // Where this comment is written
    public CommentUsagesWindow(String text){
        this(TR.tr("textTab.usages.title"), shorten(text), (onFolder, onDone) -> CommentUsages.search(text, onFolder, onDone), TR.tr("textTab.usages.none"));
    }
    public CommentUsagesWindow(String header, String subHeader, Source source, String emptyText){
        this(header, subHeader, source, emptyText, null);
    }
    public CommentUsagesWindow(String header, String subHeader, Source source, String emptyText, Actions actions){
        // Tall from the start: the previews arrive after the window is shown
        super(new VBox(10), StageWidth.ULTRA_LARGE, getInitialHeight(), header, header, subHeader);
        this.source = source;
        this.emptyText = emptyText;
        this.actions = actions;
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
        if(actions != null && !actions.buttons().isEmpty()){
            ArrayList<Button> buttons = new ArrayList<>(actions.buttons());
            buttons.add(close);
            setButtons(buttons.toArray(Button[]::new));
        }else setButtons(close);

        source.load(this::addFolder, () -> status.setText(copies == 0 ? emptyText
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
        // By identity: two occurrences at the same place are two previews
        IdentityHashMap<CommentUsages.Usage, StackPane> previews = new IdentityHashMap<>();
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
        // Size of the preview, until it is rendered
        preview.prefWidthProperty().bind(cardWidth);
        preview.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> cardWidth.get() * (zoom.getValue() < 1.01 ? 1.41 : 0.6),
                cardWidth, zoom.valueProperty()));
        preview.setMinHeight(60);

        StringBuilder caption = new StringBuilder(usage.copy().getName().replaceFirst("(?i)\\.pdf$", ""));
        if(usage.page() >= 0) caption.append("  ·  ").append(TR.tr("notes.context.page", String.valueOf(usage.page() + 1)));
        if(usage.exercise() != null) caption.append("  ·  ").append(usage.exercise());
        File openCopy = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile().getAbsoluteFile() : null;
        if(usage.copy().equals(openCopy)) caption.append("  ").append(TR.tr("textTab.usages.thisCopy"));
        if(usage.detail() != null) caption.append("  ·  ").append(usage.detail());
        Label label = new Label(caption.toString());
        label.maxWidthProperty().bind(cardWidth);
        label.setStyle("-fx-font-size: 12;");

        StackPane holder = new StackPane(preview);
        VBox card = new VBox(4, holder, label);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(5));
        card.setCursor(Cursor.HAND);
        card.setStyle("-fx-border-color: rgba(128,128,128,.45); -fx-border-radius: 5; -fx-background-radius: 5;");
        Tooltip.install(card, new Tooltip(TR.tr(actions != null && actions.cardMenu() != null ? "textTab.usages.openOrMenu" : "textTab.usages.openOrNote")));
        card.setOnMouseClicked(e -> {
            if(e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            close();
            EvaluationComments.openCopyAt(usage.copy(), usage.page());
        });
        Card handle = new Card(card, holder, preview);
        card.setOnContextMenuRequested(e -> {
            List<javafx.scene.control.MenuItem> items = new ArrayList<>();
            if(handle.isDone()){
                javafx.scene.control.MenuItem undo = new javafx.scene.control.MenuItem(TR.tr("textTab.usages.undo"));
                undo.setOnAction(a -> handle.undo());
                items.add(undo);
            }else if(actions != null && actions.cardMenu() != null) items.addAll(actions.cardMenu().apply(usage, handle));
            if(!items.isEmpty()) items.add(new javafx.scene.control.SeparatorMenuItem());
            items.addAll(getNoteItems(usage, preview));
            new javafx.scene.control.ContextMenu(items.toArray(javafx.scene.control.MenuItem[]::new)).show(card, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return card;
    }

    // Personal notes on the copy of a preview, without opening it: with its text only, or with the part of the page shown
    private static List<javafx.scene.control.MenuItem> getNoteItems(CommentUsages.Usage usage, StackPane preview){
        javafx.scene.control.MenuItem note = new javafx.scene.control.MenuItem(TR.tr("notes.previewMenu.note"));
        note.setOnAction(e -> TeacherNotes.captureNoteOn(usage.copy(), usage.page(), usage.exercise(), null));
        javafx.scene.control.MenuItem screenshot = new javafx.scene.control.MenuItem(TR.tr("notes.previewMenu.screenshot"));
        javafx.scene.image.Image image = getShownImage(preview);
        screenshot.setDisable(image == null);
        screenshot.setOnAction(e -> TeacherNotes.captureNoteOn(usage.copy(), usage.page(), usage.exercise(), image));
        return List.of(note, screenshot);
    }
    private static javafx.scene.image.Image getShownImage(StackPane preview){
        if(preview.getChildren().isEmpty() || !(preview.getChildren().getFirst() instanceof ImageView view) || view.getImage() == null) return null;
        javafx.scene.image.Image image = view.getImage();
        Rectangle2D viewport = view.getViewport();
        if(viewport == null) return image;
        return new javafx.scene.image.WritableImage(image.getPixelReader(), (int) viewport.getMinX(), (int) viewport.getMinY(),
                (int) viewport.getWidth(), (int) viewport.getHeight());
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

    // Zoomed in, the previews get wider than high, so that more copies can be compared on the screen.
    static Rectangle2D getViewport(PagePreview.Preview page, double zoom){
        double width = page.image().getWidth(), height = page.image().getHeight();
        if(zoom <= 1.01) return new Rectangle2D(0, 0, width, height);
        double viewWidth = width / zoom;
        double viewHeight = Math.min(height / zoom, viewWidth * Math.max(0.45, 1.6 / zoom));
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
