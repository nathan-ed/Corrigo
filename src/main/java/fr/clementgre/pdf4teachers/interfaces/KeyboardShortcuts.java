/*
 * Copyright (c) 2019-2023. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.interfaces;


import fr.clementgre.pdf4teachers.panel.sidebar.grades.tags.TagPicker;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.components.KeyableHBox;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.editions.elements.VectorElement;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.SideBar;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments.ScoredComments;
import fr.clementgre.pdf4teachers.panel.sidebar.notes.TeacherNotes;
import fr.clementgre.pdf4teachers.panel.sidebar.paint.gridviewfactory.ImageGridElement;
import fr.clementgre.pdf4teachers.panel.sidebar.paint.gridviewfactory.VectorGridElement;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.TextTreeItem;
import fr.clementgre.pdf4teachers.utils.MathUtils;
import fr.clementgre.pdf4teachers.utils.keyboard.CustomKeyCombination;
import fr.clementgre.pdf4teachers.utils.keyboard.KeyCodesCombination;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class KeyboardShortcuts {
    
    // Shortcuts are checked on the scene event filter. They have the higher priority in the whole app.
    private final ArrayList<ShortcutRecord> shortcuts = new ArrayList<>();
    // Lazy shortcuts are checked on the scene event handler, or in the scene event filter if no classic shortcuts have been fired
    // and the target element is a SideBar, Slider, Button OR is not a Control, Element, KeyableHBox
    // These shortcuts might be used and consumed by other elements, and might not contain any modifier key.
    private final ArrayList<ShortcutRecord> lazyShortcuts = new ArrayList<>();
    // Key -> time of its last KEY_PRESSED (auto-repeat included), removed on KEY_RELEASED.
    private final Map<KeyCode, Long> heldKeys = new HashMap<>();
    private static final long KEY_REPEAT_TIMEOUT_MS = 500;
    // List of menu bar shortcuts used to detect conflicts
    private final ArrayList<ShortcutRecord> menuBarShortcuts = new ArrayList<>();
    
    public KeyboardShortcuts(Scene main){
        
        /*******************************/
        /* Graphics elements shortcuts */
        /*******************************/
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.elements.customGraphicsElements"),
                new CustomKeyCombination(e -> {
                    return matchVectorShortcut(e).isPresent() || matchImageShortcut(e).isPresent();
                }, KeyCodeCombination.SHORTCUT_DOWN), this::firePaintElementsShortcut));
        
        /******************************/
        /***** Elements shortcuts *****/
        /******************************/
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.elements.newText"),
                new KeyCodeCombination(KeyCode.T, KeyCodeCombination.SHORTCUT_DOWN, KeyCodesCombination.SHIFT_ANY), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            SideBar.selectTab(MainWindow.textTab);
            TextElement element = MainWindow.textTab.newTextElement(!e.isShiftDown());
            element.setRealX(element.getPage().getNewElementXOnGrid(false));
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.elements.newVectorDrawing"),
                new KeyCodeCombination(KeyCode.D, KeyCodeCombination.SHORTCUT_DOWN, KeyCodesCombination.SHIFT_ANY), e -> {
            SideBar.selectTab(MainWindow.paintTab);
            MainWindow.paintTab.newVectorDrawing(e.isShiftDown());
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.elements.selectNextGrade"),
                new KeyCodeCombination(KeyCode.N, KeyCodeCombination.SHORTCUT_DOWN), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            SideBar.selectTab(MainWindow.gradeTab);
            MainWindow.gradeTab.treeView.getSelectionModel().select(GradeTreeView.getNextLogicGradeNonNull());
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.elements.createSameLevelGrade"),
                new KeyCodeCombination(KeyCode.G, KeyCodeCombination.SHORTCUT_DOWN), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            if(!MainWindow.gradeTab.isSelected()) MainWindow.gradeTab.select();
            
            GradeTreeItem item = (GradeTreeItem) MainWindow.gradeTab.treeView.getSelectionModel().getSelectedItem();
            if(item == null || item.isRoot()){
                item = MainWindow.gradeTab.treeView.getRootTreeItem(); // In case item == null
                
                GradeElement element = MainWindow.gradeTab.newGradeElementAuto(item);
                element.select();
                // Update total (Fix the bug when a total is predefined (with no children))
                item.makeSum(false);
            }else{
                GradeElement element = MainWindow.gradeTab.newGradeElementAuto(((GradeTreeItem) item.getParent()));
                element.select();
            }
            e.consume();
        }));
        
        /******************************/
        /**** Navigation shortcuts ****/
        /******************************/
        //  +/- or arrows with shortcut for zoom and reset zoom
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.zoomLess"),
                new CustomKeyCombination(e -> {
            return e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT || "-".equals(e.getText());
        }, KeyCodeCombination.SHORTCUT_DOWN), e -> {
            MainWindow.mainScreen.zoomLess();
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.zoomMore"),
                new CustomKeyCombination(e -> {
            return e.getCode() == KeyCode.UP || e.getCode() == KeyCode.KP_UP || e.getCode() == KeyCode.PLUS
                    || e.getCode() == KeyCode.ADD || "+".equals(e.getText()) || e.getCode() == KeyCode.EQUALS;
        }, KeyCodeCombination.SHORTCUT_DOWN), e -> {
            MainWindow.mainScreen.zoomMore();
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.fitWidth"),
                new KeyCodesCombination(KeyCode.DOWN, KeyCode.KP_DOWN, KeyCodeCombination.SHORTCUT_DOWN), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            MainWindow.mainScreen.zoomOperator.fitWidth(false, false);
            e.consume();
        }));
        // Arrows with ALT
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.pageUp"),
                new KeyCodesCombination(KeyCode.UP, KeyCode.KP_UP,
                KeyCodesCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            MainWindow.mainScreen.pageUp();
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.pageDown"),
                new KeyCodesCombination(KeyCode.DOWN, KeyCode.KP_DOWN,
                KeyCodesCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            MainWindow.mainScreen.pageDown();
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.previousFile"),
                new KeyCodesCombination(KeyCode.LEFT, KeyCode.KP_LEFT,
                KeyCodesCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            oncePerKeyPress(e, () -> MainWindow.filesTab.loadPreviousFile());
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.nextFile"),
                new KeyCodesCombination(KeyCode.RIGHT, KeyCode.KP_RIGHT, KeyCodesCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            oncePerKeyPress(e, () -> MainWindow.filesTab.loadNextFile());
        }));
        // Previous/next file, staying on the same page (or on the selected exercise page in exercise correction mode).
        // Ctrl+Alt(+Shift)+Arrows are often caught by Linux desktops (workspace switching): Alt+PageUp/PageDown also work.
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.previousFileKeepPage"),
                new KeyCodesCombination(KeyCode.LEFT, KeyCode.KP_LEFT,
                KeyCodesCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN, KeyCodesCombination.SHIFT_DOWN), e -> {
            oncePerKeyPress(e, () -> MainWindow.filesTab.openNeighborFile(-1, true));
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.nextFileKeepPage"),
                new KeyCodesCombination(KeyCode.RIGHT, KeyCode.KP_RIGHT,
                KeyCodesCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN, KeyCodesCombination.SHIFT_DOWN), e -> {
            oncePerKeyPress(e, () -> MainWindow.filesTab.openNeighborFile(1, true));
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.previousFileKeepPage"),
                new KeyCodeCombination(KeyCode.PAGE_UP, KeyCodesCombination.ALT_DOWN), e -> {
            oncePerKeyPress(e, () -> MainWindow.filesTab.openNeighborFile(-1, true));
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.nextFileKeepPage"),
                new KeyCodeCombination(KeyCode.PAGE_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            oncePerKeyPress(e, () -> MainWindow.filesTab.openNeighborFile(1, true));
        }));
        // Exercise correction mode: select the previous/next exercise
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.previousExercise"),
                new KeyCodesCombination(KeyCode.UP, KeyCode.KP_UP, KeyCodesCombination.ALT_DOWN), e -> {
            if(canSelectExerciseOnNode(Main.window.getScene().getFocusOwner()) && MainWindow.footerBar.selectNeighborExercise(-1)) e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.nextExercise"),
                new KeyCodesCombination(KeyCode.DOWN, KeyCode.KP_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            if(canSelectExerciseOnNode(Main.window.getScene().getFocusOwner()) && MainWindow.footerBar.selectNeighborExercise(1)) e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.gradingPanel"),
                new KeyCodeCombination(KeyCode.G, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            MainWindow.gradingPanel.focusPanel();
            e.consume();
        }));
        // Alt+1 to 9: jump to the page of the n-th exercise
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.jumpToExercise"),
                new CustomKeyCombination(e -> {
            Integer number = MathUtils.parseIntFromKeyEventOrNull(e);
            return number != null && number >= 1 && number <= 9;
        }, KeyCodesCombination.ALT_DOWN), e -> {
            if(MainWindow.gradingPanel.jumpToExercise(MathUtils.parseIntFromKeyEventOrNull(e) - 1)) e.consume();
        }));
        
        /******************************/
        /******* Notes shortcuts ******/
        /******************************/
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.notes.quickNote"),
                new KeyCodeCombination(KeyCode.N, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN), e -> {
            e.consume();
            TeacherNotes.captureTextNote();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.notes.screenshotNote"),
                new KeyCodeCombination(KeyCode.S, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN), e -> {
            e.consume();
            TeacherNotes.captureScreenshotNote();
        }));
        
        // Methods and mistakes of the exercise: # (below) or Ctrl+Shift+M
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.tags.picker"),
                new KeyCodeCombination(KeyCode.M, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            e.consume();
            TagPicker.open();
        }));
        
        // Begin/End and Page Up/Page Down
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.begin"),
                new KeyCodesCombination(KeyCode.BEGIN, KeyCode.HOME), e -> {
            if(!canBeginEndOnNode(Main.window.getScene().getFocusOwner())){ // Do not execute custom actions if a text field or a spinner is focused.
                e.consume();
                MainWindow.mainScreen.navigateBegin();
            }
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.end"),
                new KeyCodeCombination(KeyCode.END), e -> {
            if(!canBeginEndOnNode(Main.window.getScene().getFocusOwner())){ // Do not execute custom actions if a text field or a spinner is focused.
                e.consume();
                MainWindow.mainScreen.navigateEnd();
            }
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.pageUp"),
                new KeyCodeCombination(KeyCode.PAGE_UP), e -> {
            MainWindow.mainScreen.pageUp();
            e.consume();
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.navigation.pageDown"),
                new KeyCodeCombination(KeyCode.PAGE_DOWN), e -> {
            MainWindow.mainScreen.pageDown();
            e.consume();
        }));
        
        /******************************/
        /****** Number shortcuts ******/
        /******************************/
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.numbers"),
                new CustomKeyCombination(e -> {
            return MathUtils.parseIntFromKeyEventOrNull(e) != null;
        }, KeyCodeCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_ANY), e -> {
            if(!numberPressed(MathUtils.parseIntFromKeyEventOrNull(e), e.isAltDown())) return;
            e.consume();
        }));
        
        /******************************/
        /**** Elements color/size *****/
        /******************************/
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.elements.decrementSize"),
                new CustomKeyCombination(e -> {
            return e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT || "-".equals(e.getText()) || "—".equals(e.getText());
        }, KeyCodeCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            if(MainWindow.mainScreen.getSelected() instanceof TextElement){
                MainWindow.textTab.sizeSpinner.decrement();
                e.consume();
            }else if(MainWindow.mainScreen.getSelected() instanceof VectorElement){
                MainWindow.paintTab.vectorStrokeWidth.decrement();
                e.consume();
            }
        }));
        shortcuts.add(new ShortcutRecord(TR.tr("shortcuts.elements.incrementSize"),
                new CustomKeyCombination(e -> {
            return e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD || "+".equals(e.getText()) || e.getCode() == KeyCode.EQUALS;
        }, KeyCodeCombination.SHORTCUT_DOWN, KeyCodesCombination.ALT_DOWN), e -> {
            if(MainWindow.mainScreen.getSelected() instanceof TextElement){
                MainWindow.textTab.sizeSpinner.increment();
                e.consume();
            }else if(MainWindow.mainScreen.getSelected() instanceof VectorElement){
                MainWindow.paintTab.vectorStrokeWidth.increment();
                e.consume();
            }
        }));
        
        
        /*******************************/
        /********** LAZY: TAB **********/
        /*******************************/
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodeCombination(KeyCode.TAB), e -> {
            if(!MainWindow.textTab.isSelected()){
                MainWindow.textTab.select();
                e.consume();
            }else if(!MainWindow.paintTab.isSelected()){
                MainWindow.paintTab.select();
                e.consume();
            }
        }));
        
        /*******************************/
        /*** LAZY: Pages Navigation ****/
        /*******************************/
        // Pages navigation
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodesCombination(KeyCode.UP, KeyCode.KP_UP), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            MainWindow.mainScreen.navigateUp();
            e.consume();
        }));
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodesCombination(KeyCode.DOWN, KeyCode.KP_DOWN), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            MainWindow.mainScreen.navigateDown();
            e.consume();
        }));
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodesCombination(KeyCode.LEFT, KeyCode.KP_LEFT), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            // In grid view and edit pages mode, arrows move between pages; in column view, between files.
            if(MainWindow.mainScreen.isMultiPagesMode() || MainWindow.mainScreen.isEditPagesMode()) MainWindow.mainScreen.navigateLeft();
            else oncePerKeyPress(e, () -> MainWindow.filesTab.openNeighborFile(-1, true));
            e.consume();
        }));
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodesCombination(KeyCode.RIGHT, KeyCode.KP_RIGHT), e -> {
            if(!MainWindow.mainScreen.hasDocument(false)) return;
            // In grid view and edit pages mode, arrows move between pages; in column view, between files.
            if(MainWindow.mainScreen.isMultiPagesMode() || MainWindow.mainScreen.isEditPagesMode()) MainWindow.mainScreen.navigateRight();
            else oncePerKeyPress(e, () -> MainWindow.filesTab.openNeighborFile(1, true));
            e.consume();
        }));
        
        /*******************************/
        /*** LAZY: Scored comments *****/
        /*******************************/
        // 1 to 9 place the n-th entry listed in the scored comments panel at the mouse position.
        lazyShortcuts.add(new ShortcutRecord("",
                new CustomKeyCombination(e -> {
            Integer number = MathUtils.parseIntFromKeyEventOrNull(e);
            return number != null && number >= 1 && number <= 9;
        }, KeyCodesCombination.SHIFT_ANY), e -> {
            if(!MainWindow.mainScreen.hasDocument(false) || MainWindow.mainScreen.isEditPagesMode()) return;
            // Text fields don't consume the KEY_PRESSED of the characters they receive.
            Node focus = Main.window.getScene().getFocusOwner();
            if(focus instanceof TextInputControl || focus instanceof Spinner<?> || focus instanceof ComboBoxBase<?>) return;
            if(MainWindow.gradeTab.scoredCommentPanel.placeVisibleEntry(MathUtils.parseIntFromKeyEventOrNull(e) - 1)) e.consume();
        }));
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodeCombination(KeyCode.ESCAPE), e -> {
            if(ScoredComments.getArmed() != null){
                ScoredComments.disarm();
                e.consume();
            }
        }));
        
        /*******************************/
        /**** LAZY: Edit pages mode ****/
        /*******************************/
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodeCombination(KeyCode.DELETE), e -> {
            if(MainWindow.mainScreen.hasDocument(false) && MainWindow.mainScreen.isEditPagesMode()){
                MainWindow.mainScreen.document.pdfPagesRender.editor.deleteSelectedPages();
                e.consume();
            }
        }));
        lazyShortcuts.add(new ShortcutRecord("",
                new KeyCodeCombination(KeyCode.A, KeyCodeCombination.SHORTCUT_DOWN), e -> {
            if(MainWindow.mainScreen.hasDocument(false) && MainWindow.mainScreen.isEditPagesMode()){
                MainWindow.mainScreen.document.selectAll();
                e.consume();
            }
        }));
        
        
        /******************************/
        /*********** EVENTS ***********/
        /******************************/
        
        main.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            Optional<ShortcutRecord> first = shortcuts.stream()
                    .filter(entry -> entry.getCombination().match(e))
                    .filter(entry -> {
                        entry.getAction().accept(e);
                        return e.isConsumed();
                    }).findFirst();
            
            if(first.isEmpty()){
                if(Main.window.getScene().getFocusOwner() instanceof SideBar
                        || Main.window.getScene().getFocusOwner() instanceof Slider
                        || Main.window.getScene().getFocusOwner() instanceof Button
                        || (!(Main.window.getScene().getFocusOwner() instanceof Control)
                        && !(Main.window.getScene().getFocusOwner() instanceof Element)
                        && !(Main.window.getScene().getFocusOwner() instanceof KeyableHBox)
                )){
                    processLazyShortcuts(e);
                }
            }
        });
        
        main.setOnKeyPressed(this::processLazyShortcuts);
        // # (typed, whatever the keyboard layout): methods and mistakes, when no text is being typed
        main.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if(!"#".equals(e.getCharacter()) || !MainWindow.mainScreen.hasDocument(false)) return;
            Node focus = Main.window.getScene().getFocusOwner();
            if(focus instanceof TextInputControl || focus instanceof Spinner<?> || focus instanceof ComboBoxBase<?>) return;
            e.consume();
            TagPicker.open();
        });
        main.addEventFilter(KeyEvent.KEY_RELEASED, e -> heldKeys.remove(e.getCode()));
        
    }
    // Holding a key must not open dozens of files: the action is only run on the first KEY_PRESSED, until the key is released.
    // A key whose KEY_RELEASED was lost (e.g. caught by a dialog) is considered released after KEY_REPEAT_TIMEOUT_MS without events.
    private void oncePerKeyPress(KeyEvent e, Runnable action){
        e.consume();
        long now = System.currentTimeMillis();
        Long lastPress = heldKeys.put(e.getCode(), now);
        if(lastPress != null && now - lastPress < KEY_REPEAT_TIMEOUT_MS) return; // Auto-repeat of a held key
        action.run();
    }
    public void processLazyShortcuts(KeyEvent e){
        Optional<ShortcutRecord> first = lazyShortcuts.stream()
                .filter(entry -> entry.getCombination().match(e))
                .filter(entry -> {
                    entry.getAction().accept(e);
                    return e.isConsumed();
                }).findFirst();
        
    }
    // Returns String if used, null otherwise
    public String getShortcutNameIfExists(KeyCodeCombination combination){
        if(combination == null) return null;
        // KeyCombination::equals could be used, but it would not work for CustomKeyCombination that require events
        KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED,
                combination.getCode().getChar(), combination.getCode().getChar(), combination.getCode(),
                combination.getShift() == KeyCombination.ModifierValue.DOWN,
                combination.getControl() == KeyCombination.ModifierValue.DOWN,
                combination.getAlt() == KeyCombination.ModifierValue.DOWN,
                combination.getMeta() == KeyCombination.ModifierValue.DOWN);
        
        Optional<ShortcutRecord> first = Stream.concat(shortcuts.stream(), menuBarShortcuts.stream())
                .filter(s -> s.combination.match(event) && !s.getName().isEmpty())
                .findFirst();
        return first.map(ShortcutRecord::getName).orElse(null);
    }
    
    // Paint elements shortcuts
    
    private Optional<VectorGridElement> matchVectorShortcut(KeyEvent e){
        return MainWindow.paintTab.favouriteVectors.getList().getAllItems().stream()
                .filter(vector ->
                        vector.getVectorData().getKeyCodeCombination() != null && vector.getVectorData().getKeyCodeCombination().match(e))
                .findFirst();
    }
    private Optional<ImageGridElement> matchImageShortcut(KeyEvent e){
        return MainWindow.paintTab.favouriteImages.getList().getAllItems().stream()
                .filter(image ->
                        image.getImageData().getKeyCodeCombination() != null && image.getImageData().getKeyCodeCombination().match(e))
                .findFirst();
    }
    private void firePaintElementsShortcut(KeyEvent e){
        Optional<VectorGridElement> vectorData = matchVectorShortcut(e);
        vectorData.ifPresent(data -> {
            data.addToDocument(false, true);
            e.consume();
        });
        if(vectorData.isPresent()) return;
        
        Optional<ImageGridElement> imageData = matchImageShortcut(e);
        imageData.ifPresent(data -> {
            data.addToDocument(true);
            e.consume();
        });
    }
    
    private boolean numberPressed(int i, boolean alt){
        
        if(alt){ // Shortcut with Alt -> Change element color
            if(!MainWindow.mainScreen.hasDocument(false)) return false;
            
            if(MainWindow.mainScreen.getSelected() instanceof TextElement){
                MainWindow.textTab.colorPicker.selectCustomColor(i - 1);
            }else if(MainWindow.mainScreen.getSelected() instanceof VectorElement){
                if(i == 0){
                    MainWindow.paintTab.doFillButton.selectedProperty().setValue(!MainWindow.paintTab.doFillButton.isSelected());
                }else{
                    MainWindow.paintTab.vectorFillColor.selectCustomColor(i - 1);
                }
            }
            
        }else{ // Shortcut without Alt -> Add favorite text element
            if(!MainWindow.mainScreen.hasDocument(false)) return false;
            
            if(MainWindow.textTab.treeView.favoritesSection.sortToggleBtn.isSelected()) i++;
            if(i <= MainWindow.textTab.treeView.favoritesSection.getChildren().size() && i != 0){
                ((TextTreeItem) MainWindow.textTab.treeView.favoritesSection.getChildren().get(i - 1)).addToDocument(false, false);
                MainWindow.textTab.selectItem();
            }else{
                return false;
            }
        }
        return true;
    }
    
    // Alt+Up/Down is used by text fields and combo boxes (opening the popup).
    private boolean canSelectExerciseOnNode(Node node){
        return !canBeginEndOnNode(node) && !(node instanceof ComboBoxBase<?>);
    }
    private boolean canBeginEndOnNode(Node node){
        if(node instanceof TextInputControl) return true;
        else if(node instanceof Spinner<?> spinner){
            return spinner.isEditable();
        }
        return false;
    }
    public void registerMenuBarShortcut(KeyCombination combination, String name){
        menuBarShortcuts.add(new ShortcutRecord(name, combination));
    }

    private static class ShortcutRecord {
        private final String name;
        
        private final KeyCombination combination;
        private Consumer<KeyEvent> action = null;
        public ShortcutRecord(String name, KeyCombination combination, Consumer<KeyEvent> action){
            this.name = name;
            this.combination = combination;
            this.action = action;
        }
        public ShortcutRecord(String name, KeyCombination combination){
            this.name = name;
            this.combination = combination;
        }
        public String getName(){
            return name;
        }
        public KeyCombination getCombination(){
            return combination;
        }
        public Consumer<KeyEvent> getAction(){
            return action;
        }
    }
}
