/*
 * Copyright (c) 2021-2024. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 * Modified by Nathan, 2025-2026.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts;

import fr.clementgre.pdf4teachers.utils.MathText;
import javafx.embed.swing.SwingFXUtils;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.scene.text.Text;
import javafx.scene.layout.FlowPane;
import javafx.scene.image.ImageView;
import fr.clementgre.pdf4teachers.document.editions.elements.MixedTextRenderer;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.components.FontComboBox;
import fr.clementgre.pdf4teachers.components.ShortcutsTextArea;
import fr.clementgre.pdf4teachers.components.ShortcutsTextField;
import fr.clementgre.pdf4teachers.components.SyncColorPicker;
import fr.clementgre.pdf4teachers.datasaving.settings.Settings;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.autotips.AutoTipsManager;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.MainScreen.MainScreen;
import fr.clementgre.pdf4teachers.panel.sidebar.SideTab;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.TreeViewSections.TextTreeSection;
import fr.clementgre.pdf4teachers.utils.PlatformUtils;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import fr.clementgre.pdf4teachers.utils.image.ImageUtils;
import fr.clementgre.pdf4teachers.utils.interfaces.StringToDoubleConverter;
import fr.clementgre.pdf4teachers.utils.panes.PaneUtils;
import fr.clementgre.pdf4teachers.utils.svg.SVGPathIcons;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.regex.Pattern;

public class TextTab extends SideTab {

    public VBox pane = new VBox();
    public VBox optionPane = new VBox();

    private final HBox combosBox = new HBox();
    public FontComboBox fontCombo = new FontComboBox(true);
    public final Spinner<Double> sizeSpinner = new Spinner<>(2d, 999d, 14d, 2d);

    private final HBox colorAndParamsBox = new HBox();
    public final SyncColorPicker colorPicker = new SyncColorPicker();
    private final ToggleButton boldBtn = new ToggleButton("");
    private final ToggleButton itBtn = new ToggleButton("");

    public TextArea txtArea = new ShortcutsTextArea();
    // Measures the wrapped text of txtArea, to fit its height to the text
    private final Text txtAreaMeasure = new Text();
    private static final int TXT_AREA_MAX_LINES = 6;

    // Formula tools and preview, under the text area
    private final FlowPane toolsBox = new FlowPane(3, 3);
    private final ToggleButton mathToggle = new ToggleButton("∑");
    private final VBox previewBox = new VBox(3);
    private final ImageView preview = new ImageView();
    private final Label previewError = new Label();
    private TextElement previewedElement;
    // Symbol, and its LaTeX for the fonts that don't have it
    private static final String[][] SYMBOLS = {{"√", "\\surd"}, {"≠", "\\neq"}, {"≤", "\\leq"}, {"≥", "\\geq"}, {"≈", "\\approx"}, {"×", "\\times"},
            {"→", "\\to"}, {"⇒", "\\Rightarrow"}, {"⇔", "\\Leftrightarrow"}, {"∈", "\\in"}, {"ℝ", "\\mathbb{R}"}};

    private final HBox btnBox = new HBox();
    private final Button deleteBtn = new Button(TR.tr("actions.delete"));
    private final Button copyToFilesBtn = new Button(TR.tr("textTab.copyToFilesDialog.accessButton"));
    public Button newBtn = new Button(TR.tr("actions.new"));

    public static final String TEXT_TREE_ITEM_DRAG_KEY = "TextTreeItemDrag";
    public static TextTreeItem draggingItem;
    public static TextElement draggingElement;

    // FIELDS

    public boolean isNew;

    // TREEVIEW
    public TextTreeView treeView;

    // OTHER

    // AUTOCOMPLETE DROPDOWN
    private final Popup autocompletePopup = new Popup();
    private final ListView<TextTreeItem> autocompleteList = new ListView<>();
    private boolean isSelectingFromPopup = false;

    public TextTab(){
        super("text", SVGPathIcons.TEXT_LETTER, 26, 460/500d);

        draggingItem = null;
        draggingElement = null;
        setContent(pane);
        setup();

        pane.getChildren().addAll(optionPane, treeView);
    }

    public void setup(){
        treeView = new TextTreeView(pane);
        optionPane.setMinWidth(200);

        // Setup autocomplete popup
        setupAutocompletePopup();

        PaneUtils.setHBoxPosition(fontCombo, -1, 30, 2.5);
        fontCombo.setStyle("-fx-font-size: 13; -fx-border: null; -fx-padding: 0 4;");
        fontCombo.setMaxHeight(25);
        fontCombo.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.selectedProperty().get() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));
        fontCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(isNew) MainWindow.userData.textLastFontName = newValue;
        });

        PaneUtils.setHBoxPosition(sizeSpinner, 95, 30, 2.5);
        ShortcutsTextField.registerNewInput(sizeSpinner);
        sizeSpinner.setStyle("-fx-font-size: 13");
        sizeSpinner.setEditable(true);
        sizeSpinner.getValueFactory().setConverter(new StringToDoubleConverter(14));
        sizeSpinner.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.selectedProperty().get() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));
        sizeSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(isNew) MainWindow.userData.textLastFontSize = newValue;
        });

        PaneUtils.setHBoxPosition(colorPicker, -1, 30, 2.5);
        colorPicker.setStyle("-fx-font-size: 13");
        colorPicker.setValue(Color.BLACK);
        colorPicker.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.selectedProperty().get() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));
        colorPicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(isNew) MainWindow.userData.textLastFontColor = newValue.toString();
        });

        PaneUtils.setHBoxPosition(boldBtn, 45, 29, 2.5);
        boldBtn.setCursor(Cursor.HAND);
        boldBtn.setGraphic(ImageUtils.buildImage(String.valueOf(getClass().getResource("/img/TextTab/bold.png")), 0, 0, ImageUtils.defaultFullDarkColorAdjust));
        boldBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.selectedProperty().get() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));
        boldBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if(isNew) MainWindow.userData.textLastFontBold = newValue;
        });

        PaneUtils.setHBoxPosition(itBtn, 45, 29, 2.5);
        itBtn.setCursor(Cursor.HAND);
        itBtn.setGraphic(ImageUtils.buildImage(String.valueOf(getClass().getResource("/img/TextTab/italic.png")), 0, 0, ImageUtils.defaultFullDarkColorAdjust));
        itBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.selectedProperty().get() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));
        itBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if(isNew) MainWindow.userData.textLastFontItalic = newValue;
        });

        PaneUtils.setHBoxPosition(txtArea, -1, 30, 0);
        if(Main.settings.textSmall.getValue()) txtArea.setStyle("-fx-font-size: 12");
        else txtArea.setStyle("-fx-font-size: 13");
        txtArea.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.getSelected() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));
        txtArea.setPromptText(TR.tr("textTab.textAreaPromptText"));
        // Long comments are wrapped, and the text area grows up to TXT_AREA_MAX_LINES lines
        txtArea.setWrapText(true);
        txtArea.widthProperty().addListener((observable, oldValue, newValue) -> updateTextAreaHeight());
        txtArea.fontProperty().addListener((observable, oldValue, newValue) -> updateTextAreaHeight());
        txtArea.setFocusTraversable(false);

        setupFormulaTools();

        PaneUtils.setHBoxPosition(deleteBtn, -1, 30, 2.5);
        deleteBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.selectedProperty().get() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));

        PaneUtils.setHBoxPosition(copyToFilesBtn, -1, 30, 2.5);
        copyToFilesBtn.setTooltip(new Tooltip(TR.tr("textTab.copyToFilesDialog.accessButton.tooltip")));
        copyToFilesBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> MainWindow.mainScreen.selectedProperty().get() == null || !(MainWindow.mainScreen.getSelected() instanceof TextElement), MainWindow.mainScreen.selectedProperty()));

        PaneUtils.setHBoxPosition(newBtn, -1, 30, 2.5);
        newBtn.disableProperty().bind(MainWindow.mainScreen.statusProperty().isNotEqualTo(MainScreen.Status.OPEN));

        combosBox.getChildren().addAll(fontCombo, sizeSpinner);
        colorAndParamsBox.getChildren().addAll(colorPicker, boldBtn, itBtn);
        btnBox.getChildren().addAll(deleteBtn, copyToFilesBtn, newBtn);

        VBox.setMargin(combosBox, new Insets(2.5, 2.5, 0, 2.5));
        VBox.setMargin(colorAndParamsBox, new Insets(0, 2.5, 0, 2.5));
        VBox.setMargin(txtArea, new Insets(2.5, 5, 2.5, 5));
        VBox.setMargin(toolsBox, new Insets(0, 5, 2.5, 5));
        VBox.setMargin(previewBox, new Insets(0, 5, 2.5, 5));
        VBox.setMargin(btnBox, new Insets(0, 2.5, 7.5, 2.5));
        optionPane.getChildren().addAll(combosBox, colorAndParamsBox, txtArea, toolsBox, previewBox, btnBox);


        MainWindow.mainScreen.selectedProperty().addListener((ObservableValue<? extends Element> observable, Element oldElement, Element newElement) -> {
            isNew = false;
            if(oldElement instanceof TextElement current){
                current.textProperty().unbind();
                current.fontProperty().unbind();

                if(((TextElement) oldElement).hasEmptyText()){
                    oldElement.delete(true, UType.ELEMENT_NO_COUNT_BEFORE);
                }

                if(!(newElement instanceof TextElement)) txtArea.clear();
            }
            updatePreview(newElement instanceof TextElement text ? text : null);
            if(newElement instanceof TextElement current){

                txtArea.setText(current.getText());
                boldBtn.setSelected(FontUtils.getFontWeight(current.getFont()) == FontWeight.BOLD);
                itBtn.setSelected(FontUtils.getFontPosture(current.getFont()) == FontPosture.ITALIC);
                colorPicker.setValue(current.getColor());
                fontCombo.getSelectionModel().select(current.getFont().getFamily());
                sizeSpinner.getValueFactory().setValue(current.getFont().getSize());

                current.fontProperty().bind(Bindings.createObjectBinding(() -> {
                    Edition.setUnsave("TextElement FontChanged");
                    return getFont();
                }, fontCombo.getSelectionModel().selectedItemProperty(), sizeSpinner.valueProperty(), itBtn.selectedProperty(), boldBtn.selectedProperty()));
            }
        });

        txtArea.setContextMenu(null);

        txtArea.disableProperty().addListener((observable, oldValue, newValue) -> {
            treeView.updateAutoComplete();
            if(newValue) autocompletePopup.hide();
        });
        MainWindow.mainScreen.selectedProperty().addListener((observable, oldValue, newValue) -> {
            treeView.updateAutoComplete();
            autocompletePopup.hide();
        });

        txtArea.textProperty().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {

            if(newValue.contains("\u0009")){ // TAB
                txtArea.setText(newValue.replaceAll(Pattern.quote("\u0009"), ""));
                return;
            }

            if(MainWindow.mainScreen.getSelected() instanceof TextElement element){
                element.setText(newValue); // First: the text must never be lost because of the layout below
                // Only update autocomplete if not selecting from popup
                if(!isSelectingFromPopup){
                    treeView.updateAutoComplete();
                    updateAutocompletePopup();
                }
                updatePreview(element);
                if(new Random().nextInt(10) == 0) AutoTipsManager.showByAction("textedit");
            }
            updateTextAreaHeight();
        });

        // Event filter for KEY_PRESSED to intercept Enter key
        txtArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            // Check for ENTER or UNDEFINED with empty/newline character
            boolean isEnterKey = e.getCode() == KeyCode.ENTER ||
                                (e.getCode() == KeyCode.UNDEFINED &&
                                 (e.getCharacter().isEmpty() ||
                                  e.getCharacter().equals("\r") ||
                                  e.getCharacter().equals("\n")));
            if(!isEnterKey || !autocompletePopup.isShowing()) return;

            // Enter only picks a suggestion once the user moved into the list with UP/DOWN, otherwise it inserts a new line.
            if(shouldEnterSelectAutocomplete(true, autocompleteList.getSelectionModel().getSelectedIndex())){
                e.consume();
                selectAutocompleteItem();
            }else{
                autocompletePopup.hide();
            }
        });

        // Event filter for KEY_TYPED to catch Enter as typed character
        txtArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {

            if((e.getCharacter().equals("\r") || e.getCharacter().equals("\n"))
                    && shouldEnterSelectAutocomplete(autocompletePopup.isShowing(), autocompleteList.getSelectionModel().getSelectedIndex())){
                e.consume();
                selectAutocompleteItem();
            }
        });

        txtArea.setOnKeyPressed(e -> {
            // Handle autocomplete popup navigation
            if(autocompletePopup.isShowing()){
                if(e.getCode() == KeyCode.DOWN){
                    e.consume();
                    int currentIndex = autocompleteList.getSelectionModel().getSelectedIndex();
                    if(currentIndex == -1){
                        // No selection, select first
                        autocompleteList.getSelectionModel().selectFirst();
                        autocompleteList.scrollTo(0);
                    }else if(currentIndex < autocompleteList.getItems().size() - 1){
                        // Move down
                        autocompleteList.getSelectionModel().select(currentIndex + 1);
                        autocompleteList.scrollTo(currentIndex + 1);
                    }
                    // At last item, stay there - don't fall through to tree view navigation
                    return;
                }else if(e.getCode() == KeyCode.UP){
                    e.consume();
                    int currentIndex = autocompleteList.getSelectionModel().getSelectedIndex();
                    if(currentIndex == -1){
                        // No selection, select last
                        autocompleteList.getSelectionModel().selectLast();
                        autocompleteList.scrollTo(autocompleteList.getItems().size() - 1);
                    }else if(currentIndex > 0){
                        // Move up
                        autocompleteList.getSelectionModel().select(currentIndex - 1);
                        autocompleteList.scrollTo(currentIndex - 1);
                    }
                    // At first item, stay there - don't fall through to tree view navigation
                    return;
                }else if(e.getCode() == KeyCode.ESCAPE){
                    e.consume();
                    autocompletePopup.hide();
                    // Remove focus from txtArea to enable app-level keyboard navigation
                    MainWindow.mainScreen.requestFocus();
                    return;
                }
            }

            // ESC key when popup is NOT showing - still remove focus to enable app navigation
            if(e.getCode() == KeyCode.ESCAPE){
                e.consume();
                MainWindow.mainScreen.requestFocus();
                return;
            }

            if(e.getCode() == KeyCode.DELETE || (e.getCode() == KeyCode.BACK_SPACE && e.isShortcutDown())){
                e.consume();
                if(txtArea.getCaretPosition() == txtArea.getText().length()){
                    Element element = MainWindow.mainScreen.getSelected();
                    if(element != null){
                        MainWindow.mainScreen.setSelected(null);
                        element.delete(true, UType.ELEMENT);
                    }
                }
            }else if(e.getCode() == KeyCode.TAB){
                e.consume();
                MainWindow.paintTab.select();

            }else if(e.getCode() == KeyCode.DOWN && txtArea.getText().split("\n").length == 1){
                e.consume();
                if(TextTreeItem.lastKeyPressTime > System.currentTimeMillis() - 100) return;
                else TextTreeItem.lastKeyPressTime = System.currentTimeMillis();
                pane.requestFocus();
                if(!treeView.selectNextInSelection()){
                    txtArea.requestFocus();
                }
            }else if(e.getCode() == KeyCode.UP && txtArea.getText().split("\n").length == 1){
                e.consume();
                if(TextTreeItem.lastKeyPressTime > System.currentTimeMillis() - 100) return;
                else TextTreeItem.lastKeyPressTime = System.currentTimeMillis();
                pane.requestFocus();
                if(!treeView.selectPreviousInSelection()){
                    txtArea.requestFocus();
                }
            }
        });
        colorPicker.valueProperty().addListener(o -> {
            if(MainWindow.mainScreen.getSelected() != null){
                if(MainWindow.mainScreen.getSelected() instanceof TextElement){
                    ((TextElement) MainWindow.mainScreen.getSelected()).setColor(colorPicker.getValue());
                    Edition.setUnsave("TextElement color changed");
                }
            }
        });
        newBtn.setOnAction(e -> newTextElement(true));
        copyToFilesBtn.setOnAction(e -> new TextCopyToFilesDialog().show());
        deleteBtn.setOnAction(e -> {
            MainWindow.mainScreen.getSelected().delete(true, UType.ELEMENT);
            MainWindow.mainScreen.setSelected(null);
        });
    }

    public TextElement newTextElement(boolean addToLasts){
        PageRenderer page = MainWindow.mainScreen.document.getLastCursorOverPageObject();

        MainWindow.mainScreen.setSelected(null);

        fontCombo.getSelectionModel().select(MainWindow.userData.textLastFontName.isEmpty() ? "Open Sans" : MainWindow.userData.textLastFontName);
        sizeSpinner.getValueFactory().setValue(MainWindow.userData.textLastFontSize);
        colorPicker.setValue(Color.valueOf(MainWindow.userData.textLastFontColor.isEmpty() ? "#000000" : MainWindow.userData.textLastFontColor));
        boldBtn.setSelected(MainWindow.userData.textLastFontBold);
        itBtn.setSelected(MainWindow.userData.textLastFontItalic);

        TextElement current = new TextElement(page.getNewElementXOnGrid(true), page.getNewElementYOnGrid(), page.getPage(),
                true, "", colorPicker.getValue(), getFont(), 0);
        
        page.addElement(current, true, UType.ELEMENT);
        current.centerOnCoordinatesY();
        MainWindow.mainScreen.setSelected(current);
        isNew = true;

        txtArea.setText("");
        if(addToLasts) TextTreeView.addSavedElement(current.toNoDisplayTextElement(TextTreeSection.LAST_TYPE, true));
        txtArea.requestFocus();

        AutoTipsManager.showByAction("newtextelement");

        return current;
    }

    // Fits the height of the text area to its wrapped text, from 1 to TXT_AREA_MAX_LINES lines.
    private void updateTextAreaHeight(){
        txtAreaMeasure.setFont(txtArea.getFont());
        txtAreaMeasure.setText(" ");
        double lineHeight = txtAreaMeasure.getLayoutBounds().getHeight();
        // The text area padding and the space kept for the vertical scroll bar
        txtAreaMeasure.setWrappingWidth(Math.max(20, txtArea.getWidth() - 30));
        String text = txtArea.getText();
        txtAreaMeasure.setText(text.isEmpty() || text.endsWith("\n") ? text + " " : text);
        double textHeight = Math.clamp(txtAreaMeasure.getLayoutBounds().getHeight(), lineHeight, lineHeight * TXT_AREA_MAX_LINES);
        double height = Math.ceil(textHeight + 12);
        // PaneUtils.setHBoxPosition binds the height
        txtArea.minHeightProperty().unbind();
        txtArea.prefHeightProperty().unbind();
        txtArea.maxHeightProperty().unbind();
        if(txtArea.getMinHeight() != height || txtArea.getPrefHeight() != height){
            txtArea.setMinHeight(height);
            txtArea.setPrefHeight(height);
            txtArea.setMaxHeight(height);
        }
    }

    // FORMULAS

    private void setupFormulaTools(){
        mathToggle.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("textTab.math.toggle.tooltip")));
        mathToggle.setFocusTraversable(false);
        mathToggle.setOnAction(e -> {
            String text = txtArea.getText();
            if(mathToggle.isSelected()) txtArea.setText("$$" + text + (text.isEmpty() ? "" : "$$"));
            else txtArea.setText(unwrapWholeFormula(text));
            txtArea.requestFocus();
            txtArea.positionCaret(mathToggle.isSelected() && text.isEmpty() ? 2 : txtArea.getText().length());
        });
        Button inline = new Button("$…$");
        inline.setTooltip(PaneUtils.genWrappedToolTip(TR.tr("textTab.math.inline.tooltip")));
        inline.setOnAction(e -> {
            String selected = txtArea.getSelectedText();
            int start = txtArea.getSelection().getStart();
            txtArea.replaceSelection("$" + (selected.isEmpty() ? " " : selected) + "$");
            txtArea.requestFocus();
            if(selected.isEmpty()) txtArea.selectRange(start + 1, start + 2); // The space is replaced by what is typed
        });
        toolsBox.getChildren().addAll(mathToggle, inline);
        for(String[] symbol : SYMBOLS){
            Button button = new Button(symbol[0]);
            button.setOnAction(e -> {
                txtArea.replaceSelection(canDisplay(symbol[0]) ? symbol[0] : "$" + symbol[1] + "$");
                txtArea.requestFocus();
            });
            toolsBox.getChildren().add(button);
        }
        for(javafx.scene.Node node : toolsBox.getChildren()){
            node.setFocusTraversable(false);
            node.setStyle("-fx-padding: 1 5; -fx-font-size: 12;");
        }
        toolsBox.disableProperty().bind(txtArea.disableProperty());

        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        previewDelay.setOnFinished(e -> renderPreview());
        previewError.setWrapText(true);
        previewError.setStyle("-fx-text-fill: #c62828; -fx-font-size: 11;");
        previewError.managedProperty().bind(previewError.visibleProperty());
        previewBox.getChildren().addAll(preview, previewError);
        previewBox.managedProperty().bind(previewBox.visibleProperty());
        previewBox.setVisible(false);
    }
    // The symbol is written as a character if the font of the text has it, as LaTeX otherwise.
    private boolean canDisplay(String symbol){
        return MixedTextRenderer.canDisplay(fontCombo.getSelectionModel().getSelectedItem(), itBtn.isSelected(), boldBtn.isSelected(), symbol.codePointAt(0));
    }
    // "$$x$$" or "$$x" -> "x"
    static String unwrapWholeFormula(String text){
        if(!text.startsWith("$$")) return text;
        String inside = text.substring(2);
        return inside.endsWith("$$") ? inside.substring(0, inside.length() - 2) : inside;
    }
    // Shows the selected text with its formulas, wrapped at the width of the panel, and the LaTeX error if there is one.
    private void updatePreview(TextElement element){
        if(previewedElement != element){
            previewError.textProperty().unbind();
            previewedElement = element;
            if(element != null) previewError.textProperty().bind(element.latexErrorProperty());
            preview.setImage(null);
        }
        boolean math = element != null && element.isMath();
        previewBox.setVisible(math);
        previewError.visibleProperty().unbind();
        previewError.visibleProperty().bind(previewError.textProperty().isNotEmpty().and(previewBox.visibleProperty()));
        mathToggle.setSelected(element != null && element.getText().startsWith("$$"));
        if(math) previewDelay.playFromStart(); // Not at each typed character
        else previewDelay.stop();
    }
    private final PauseTransition previewDelay = new PauseTransition(Duration.millis(150));
    private long previewRequest;
    private void renderPreview(){
        if(!(previewedElement instanceof TextElement element) || !element.isMath()) return;
        String text = element.getText();
        boolean legacy = MathText.isLegacy(text);
        String family = element.getFont().getFamily();
        boolean bold = FontUtils.getFontWeight(element.getFont()) == FontWeight.BOLD;
        boolean italic = FontUtils.getFontPosture(element.getFont()) == FontPosture.ITALIC;
        double size = element.getFont().getSize();
        java.awt.Color color = element.getAwtColor();
        double width = Math.max(80, optionPane.getWidth() - 20);
        long request = ++previewRequest;
        new Thread(() -> {
            float scale = 2; // Sharp on high density screens
            java.awt.image.BufferedImage image = legacy ? element.renderAwtLatex()
                    : MixedTextRenderer.render(MathText.parse(text), family, bold, italic, size, color, width, scale).image();
            double displayScale = legacy ? TextElement.RENDER_FACTOR : scale;
            Platform.runLater(() -> {
                if(request != previewRequest) return;
                preview.setImage(SwingFXUtils.toFXImage(image, null));
                preview.setFitWidth(Math.min(width, image.getWidth() / displayScale));
            });
        }, "Text preview").start();
    }

    public void selectItem(){
        PlatformUtils.runLaterOnUIThread(50, () -> {
            String text = txtArea.getText();
            txtArea.setText(text);
            txtArea.positionCaret(txtArea.getText().length());
            txtArea.requestFocus();
        });
    }

    private Font getFont(){
        return FontUtils.getFont(fontCombo.getSelectionModel().getSelectedItem(), itBtn.isSelected(), boldBtn.isSelected(), sizeSpinner.getValueFactory().getValue());
    }

    private void setupAutocompletePopup(){
        autocompleteList.setPrefHeight(250);
        autocompleteList.setMaxHeight(400);
        autocompleteList.setStyle("-fx-background-color: white; -fx-border-color: #999999; -fx-border-width: 1px;");

        // Custom cell factory to display TextTreeItems
        autocompleteList.setCellFactory(param -> new ListCell<TextTreeItem>(){
            private final Label label = new Label();

            {
                // Initialize label
                label.setMaxWidth(Double.MAX_VALUE);
                label.setPadding(new Insets(3, 5, 3, 5));

                // Update label style when selection state changes
                selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                    if(getItem() != null){
                        if(isNowSelected){
                            label.setStyle("-fx-font-size: 12px; -fx-text-fill: white;");
                        }else{
                            label.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
                        }
                    }
                });
            }

            @Override
            protected void updateItem(TextTreeItem item, boolean empty){
                super.updateItem(item, empty);
                if(empty || item == null){
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                }else{
                    String displayText = item.getText();
                    // Truncate long text for display
                    if(displayText.length() > 80){
                        displayText = displayText.substring(0, 77) + "...";
                    }
                    label.setText(displayText);

                    // Update label color based on selection state
                    if(isSelected()){
                        label.setStyle("-fx-font-size: 12px; -fx-text-fill: white;");
                    }else{
                        label.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
                    }

                    setText(null);
                    setGraphic(label);
                    // Don't override background - let JavaFX handle selection highlighting
                    setStyle("-fx-padding: 2px;");
                }
            }
        });

        // Handle mouse clicks on list items
        autocompleteList.setOnMouseClicked(event -> {
            if(event.getClickCount() == 1 && autocompleteList.getSelectionModel().getSelectedItem() != null){
                selectAutocompleteItem();
            }
        });

        autocompletePopup.getContent().add(autocompleteList);
        autocompletePopup.setAutoHide(true);
        autocompletePopup.setAutoFix(true);
    }

    private void updateAutocompletePopup(){
        if(isSelectingFromPopup) return;

        String matchText = txtArea.getText();

        if(txtArea.isDisabled() || matchText.isBlank()){
            autocompletePopup.hide();
            return;
        }

        // Collect matching items from all sections, without showing the same text twice
        List<TextTreeItem> matchingItems = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();
        String lowerMatchText = matchText.toLowerCase();
        for(TextTreeSection section : List.of(treeView.favoritesSection, treeView.evaluationSection, treeView.lastsSection, treeView.onFileSection)){
            for(TextTreeItem item : section.getTextItems()){
                if(item.getCore() == MainWindow.mainScreen.getSelected()) continue;
                String text = item.getText();
                if(text.toLowerCase().contains(lowerMatchText) && seenTexts.add(text)) matchingItems.add(item);
            }
        }
        // Nothing to suggest if the only match is what is already typed
        if(matchingItems.size() == 1 && matchingItems.getFirst().getText().equals(matchText)){
            matchingItems.clear();
        }

        // Update popup
        if(matchingItems.isEmpty()){
            autocompletePopup.hide();
        }else{
            autocompleteList.getItems().setAll(matchingItems);
            autocompleteList.getSelectionModel().clearSelection();

            // Position popup below txtArea
            if(!autocompletePopup.isShowing()){
                var bounds = txtArea.localToScreen(txtArea.getBoundsInLocal());
                if(bounds != null){
                    autocompletePopup.show(txtArea, bounds.getMinX(), bounds.getMaxY());
                }
            }

            // Adjust width to match txtArea
            autocompleteList.setPrefWidth(txtArea.getWidth() - 10);
        }
    }

    static boolean shouldEnterSelectAutocomplete(boolean popupShowing, int selectedIndex){
        return popupShowing && selectedIndex >= 0;
    }

    private void selectAutocompleteItem(){
        TextTreeItem selectedItem = autocompleteList.getSelectionModel().getSelectedItem();

        if(selectedItem != null){
            isSelectingFromPopup = true;

            // Hide popup first to prevent visual glitches
            autocompletePopup.hide();

            // Set the text to the selected item's text
            String selectedText = selectedItem.getText();
            txtArea.setText(selectedText);
            txtArea.positionCaret(selectedText.length());

            // Update formatting from the selected item
            fontCombo.getSelectionModel().select(selectedItem.getFont().getFamily());
            sizeSpinner.getValueFactory().setValue(selectedItem.getFont().getSize());
            colorPicker.setValue(selectedItem.getColor());
            boldBtn.setSelected(FontUtils.getFontWeight(selectedItem.getFont()) == FontWeight.BOLD);
            itBtn.setSelected(FontUtils.getFontPosture(selectedItem.getFont()) == FontPosture.ITALIC);

            txtArea.requestFocus();

            // Clear flag after all events have been processed
            PlatformUtils.runLaterOnUIThread(50, () -> {
                isSelectingFromPopup = false;
            });
        }
    }
}
