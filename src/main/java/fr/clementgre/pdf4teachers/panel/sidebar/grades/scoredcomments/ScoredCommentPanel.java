/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import fr.clementgre.pdf4teachers.components.HBoxSpacer;
import fr.clementgre.pdf4teachers.components.IconButton;
import fr.clementgre.pdf4teachers.document.editions.elements.ScoredCommentElement;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.MainScreen.MainScreen;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.utils.panes.PaneUtils;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import fr.clementgre.pdf4teachers.utils.svg.SVGPathIcons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.*;

/**
 * Scored comments list of the Grades tab.
 * Click an entry to arm it, then click on the page to place it (Shift+click to place it several times).
 * Keys 1 to 9 place the n-th listed entry at the mouse position.
 */
public class ScoredCommentPanel extends VBox {

    private final Label title = new Label(TR.tr("scoredComments.title"));
    private final Label scope = new Label();
    private final CheckBox showAll = new CheckBox(TR.tr("scoredComments.showAll"));
    private final TextField search = new TextField();
    private final ListView<ScoredComment> list = new ListView<>();
    private final Label armedBanner = new Label();
    private final Button newEntry = new IconButton(SVGPathIcons.PLUS, TR.tr("scoredComments.new.tooltip"), e -> createEntry(null), false);
    private final Button searchCopies = new IconButton(SVGPathIcons.SEARCH, TR.tr("scoredComments.search.tooltip"), e -> new ScoredCommentSearchWindow(search.getText()), false);

    // Filter of the list: path of the exercise or sub-grade, or null for all.
    private String scopePath;
    private Map<String, Long> placedCounts = Map.of();

    public ScoredCommentPanel(){
        setSpacing(4);
        setPadding(new Insets(4, 4, 4, 4));
        setMinHeight(90);

        title.setStyle("-fx-font-weight: bold;");
        scope.setStyle("-fx-text-fill: #888;");
        HBox header = new HBox(6, title, scope, new HBoxSpacer(), showAll, searchCopies, newEntry);
        header.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText(TR.tr("scoredComments.search"));
        search.textProperty().addListener((o, oldValue, newValue) -> refresh());
        showAll.selectedProperty().addListener((o, oldValue, newValue) -> refresh());

        list.setPlaceholder(new Label(TR.tr("scoredComments.empty")));
        list.setCellFactory(l -> new EntryCell());
        list.setFocusTraversable(false);
        VBox.setVgrow(list, Priority.ALWAYS);

        armedBanner.setWrapText(true);
        armedBanner.setMaxWidth(Double.MAX_VALUE);
        armedBanner.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-padding: 4 6; -fx-background-radius: 4;");
        armedBanner.visibleProperty().bind(ScoredComments.armedProperty().isNotNull());
        armedBanner.managedProperty().bind(armedBanner.visibleProperty());
        armedBanner.setOnMouseClicked(e -> ScoredComments.disarm());

        getChildren().addAll(header, search, list, armedBanner);
        disableProperty().bind(MainWindow.mainScreen.statusProperty().isNotEqualTo(MainScreen.Status.OPEN));

        ScoredComments.revisionProperty().addListener((o, oldValue, newValue) -> refresh());
        MainWindow.mainScreen.statusProperty().addListener((o, oldValue, newValue) -> {
            ScoredComments.disarm();
            refresh();
        });
        ScoredComments.armedProperty().addListener((o, oldValue, newValue) -> {
            if(newValue != null) armedBanner.setText(TR.tr("scoredComments.armed", ScoredCommentGrades.render(newValue.getText(), newValue.getPoints(), MainWindow.gradesDigFormat)));
            updatePagesCursor(newValue != null);
            list.refresh();
        });
    }

    // Called once the grade tree view exists.
    public void setupTreeViewListener(GradeTreeView treeView){
        treeView.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) -> refresh());
    }

    private static void updatePagesCursor(boolean armed){
        if(!MainWindow.mainScreen.hasDocument(false)) return;
        for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
            page.setCursor(armed ? Cursor.CROSSHAIR : Cursor.DEFAULT);
        }
    }

    // LIST

    public void refresh(){
        if(!MainWindow.mainScreen.hasDocument(false)){
            list.getItems().clear();
            scope.setText("");
            return;
        }
        scopePath = showAll.isSelected() ? null : getScopePath();
        scope.setText(scopePath == null ? "" : "— " + ScoredCommentEditDialog.getGradeDisplayName(scopePath));

        ScoredCommentCatalog catalog = ScoredComments.getCatalog();
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        List<String> gradeOrder = ScoredCommentEditDialog.getLeafGradePaths();

        List<ScoredComment> entries = catalog.getComments().stream()
                .filter(entry -> scopePath == null || entry.getGradePath().equals(scopePath) || entry.getGradePath().startsWith(scopePath + "\\"))
                .filter(entry -> query.isEmpty() || entry.getText().toLowerCase().contains(query)
                        || ScoredCommentEditDialog.getGradeDisplayName(entry.getGradePath()).toLowerCase().contains(query))
                .sorted(Comparator.comparingInt(entry -> {
                    int index = gradeOrder.indexOf(entry.getGradePath());
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .toList();

        HashMap<String, Long> counts = new HashMap<>();
        for(ScoredCommentElement placed : ScoredComments.getPlacedComments()){
            counts.merge(placed.getScoredCommentId(), 1L, Long::sum);
        }
        placedCounts = counts;

        list.getItems().setAll(entries);
        list.refresh();
        if(ScoredComments.getArmed() != null && catalog.get(ScoredComments.getArmed().getId()).isEmpty()) ScoredComments.disarm();
    }

    // The selected exercise in exercise correction mode, otherwise the grade selected in the tree.
    private static String getScopePath(){
        if(GradeTreeView.getTotal() == null) return null;
        if(MainWindow.footerBar != null && MainWindow.footerBar.isExerciseCorrectionMode()){
            int index = MainWindow.footerBar.getSelectedExerciseIndex();
            if(index >= 0 && index < GradeTreeView.getTotal().getChildren().size()){
                return ((GradeTreeItem) GradeTreeView.getTotal().getChildren().get(index)).getCore().getPath();
            }
        }
        if(MainWindow.gradeTab.treeView.getSelectionModel().getSelectedItem() instanceof GradeTreeItem item && !item.isRoot()){
            return item.getCore().getPath();
        }
        return null;
    }
    // The selected grade if it is a sub-grade, else the first sub-grade of the scope.
    public String getDefaultGradePath(){
        if(MainWindow.gradeTab.treeView.getSelectionModel().getSelectedItem() instanceof GradeTreeItem item
                && !item.isRoot() && !item.hasSubGrade()){
            return item.getCore().getPath();
        }
        if(scopePath == null) return null;
        return ScoredCommentEditDialog.getLeafGradePaths().stream()
                .filter(path -> path.equals(scopePath) || path.startsWith(scopePath + "\\"))
                .findFirst().orElse(null);
    }

    public List<ScoredComment> getVisibleEntries(){
        return list.getItems();
    }

    // Places the n-th (starting from 0) listed entry at the mouse position. Returns false if there is no such entry.
    public boolean placeVisibleEntry(int index){
        if(!MainWindow.mainScreen.hasDocument(false) || index < 0 || index >= list.getItems().size()) return false;
        ScoredComments.placeAtMouse(list.getItems().get(index));
        return true;
    }

    // ACTIONS

    public void createEntry(String gradePath){
        ScoredCommentEditDialog.show(null, gradePath == null ? getDefaultGradePath() : gradePath).ifPresent(entry -> {
            ScoredComments.getCatalog().add(entry);
            ScoredComments.fireChanged(true);
            ScoredComments.arm(entry);
        });
    }
    public void editEntry(ScoredComment entry){
        ScoredCommentEditDialog.show(entry, null).ifPresent(edited -> {
            ScoredComments.fireChanged(true);
            new ScoredCommentPropagationDialog(edited).show();
        });
    }
    private void duplicateEntry(ScoredComment entry){
        ScoredComment copy = entry.copyWithNewId();
        ScoredComments.getCatalog().addAfter(copy, entry);
        ScoredComments.fireChanged(true);
        editEntry(copy);
    }
    public void deleteEntry(ScoredComment entry){
        long placed = placedCounts.getOrDefault(entry.getId(), 0L);
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, TR.tr("scoredComments.delete.title"),
                TR.tr("scoredComments.delete.header", ScoredCommentGrades.render(entry.getText(), entry.getPoints(), MainWindow.gradesDigFormat)),
                TR.tr("scoredComments.delete.details", (int) placed));
        alert.addCancelButton(ButtonPosition.CLOSE);
        ButtonType keep = alert.addButton(TR.tr("scoredComments.delete.keepAsText"), ButtonPosition.OTHER_RIGHT);
        ButtonType remove = alert.addButton(TR.tr("scoredComments.delete.remove"), ButtonPosition.DEFAULT);

        ButtonType choice = alert.getShowAndWait();
        if(choice != keep && choice != remove) return;
        boolean keepAsText = choice == keep;

        if(ScoredComments.getArmed() == entry) ScoredComments.disarm();
        ScoredComments.deleteEntry(entry, keepAsText);
        ScoredCommentPropagationDialog.forDelete(entry, keepAsText).show();
    }

    // CELL

    private class EntryCell extends ListCell<ScoredComment> {

        private final Label number = new Label();
        private final Label points = new Label();
        private final Label comment = new Label();
        private final Label grade = new Label();
        private final Label count = new Label();
        private final HBox root;

        EntryCell(){
            number.setMinWidth(12);
            number.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");
            points.setMinWidth(Region.USE_PREF_SIZE);
            comment.setWrapText(true);
            grade.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");
            count.setMinWidth(Region.USE_PREF_SIZE);
            count.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");
            VBox texts = new VBox(0, comment, grade);
            HBox.setHgrow(texts, Priority.ALWAYS);
            texts.setMaxWidth(Double.MAX_VALUE);
            root = new HBox(6, number, points, texts, count);
            root.setAlignment(Pos.CENTER_LEFT);

            setOnMouseClicked(e -> {
                if(getItem() == null || e.getButton() != MouseButton.PRIMARY) return;
                if(e.getClickCount() == 2){
                    ScoredComments.disarm();
                    editEntry(getItem());
                }else if(ScoredComments.getArmed() == getItem()) ScoredComments.disarm();
                else ScoredComments.arm(getItem());
            });
        }

        @Override
        protected void updateItem(ScoredComment entry, boolean empty){
            super.updateItem(entry, empty);
            if(empty || entry == null){
                setGraphic(null);
                setContextMenu(null);
                setTooltip(null);
                setStyle(null);
                return;
            }
            int index = getIndex();
            number.setText(index < 9 ? String.valueOf(index + 1) : "");

            points.setText(ScoredCommentGrades.formatPoints(entry.getPoints(), MainWindow.gradesDigFormat));
            points.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 1 5; -fx-background-radius: 8; -fx-background-color: "
                    + ScoredCommentEditDialog.toHex(ScoredComments.getColor(entry)) + ";");
            comment.setText(entry.getText().isBlank() ? "—" : entry.getText());
            comment.maxWidthProperty().bind(list.widthProperty().subtract(120));
            grade.setText(ScoredCommentEditDialog.getGradeDisplayName(entry.getGradePath()));

            long placed = placedCounts.getOrDefault(entry.getId(), 0L);
            count.setText(placed > 0 ? "×" + placed : "");

            setGraphic(root);
            setStyle(ScoredComments.getArmed() == entry ? "-fx-background-color: rgba(21, 101, 192, .25);" : null);
            setTooltip(PaneUtils.genWrappedToolTip(TR.tr("scoredComments.entry.tooltip")));
            setContextMenu(buildMenu(entry));
        }

        private ContextMenu buildMenu(ScoredComment entry){
            MenuItem edit = new MenuItem(TR.tr("actions.edit"));
            edit.setOnAction(e -> editEntry(entry));
            MenuItem duplicate = new MenuItem(TR.tr("actions.duplicate"));
            duplicate.setOnAction(e -> duplicateEntry(entry));
            MenuItem delete = new MenuItem(TR.tr("actions.delete"));
            delete.setOnAction(e -> deleteEntry(entry));
            MenuItem newForGrade = new MenuItem(TR.tr("scoredComments.menu.newForGrade"));
            newForGrade.setOnAction(e -> createEntry(entry.getGradePath()));
            MenuItem findCopies = new MenuItem(TR.tr("scoredComments.menu.findCopies"));
            findCopies.setOnAction(e -> new ScoredCommentSearchWindow(entry.getText()));

            ToggleGroup group = new ToggleGroup();
            RadioMenuItem fromMax = new RadioMenuItem(TR.tr("scoredComments.menu.baseFull"));
            RadioMenuItem fromZero = new RadioMenuItem(TR.tr("scoredComments.menu.baseZero"));
            fromMax.setToggleGroup(group);
            fromZero.setToggleGroup(group);
            boolean zero = ScoredComments.getCatalog().getBase(entry.getGradePath()) == ScoredCommentCatalog.Base.ZERO;
            (zero ? fromZero : fromMax).setSelected(true);
            fromMax.setOnAction(e -> ScoredComments.setBase(entry.getGradePath(), ScoredCommentCatalog.Base.FULL));
            fromZero.setOnAction(e -> ScoredComments.setBase(entry.getGradePath(), ScoredCommentCatalog.Base.ZERO));

            return new ContextMenu(edit, duplicate, delete, newForGrade, findCopies, new SeparatorMenuItem(), fromMax, fromZero);
        }
    }
}
