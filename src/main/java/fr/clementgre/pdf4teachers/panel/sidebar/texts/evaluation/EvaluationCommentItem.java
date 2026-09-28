/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.TextTreeItem;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.TextTreeView;
import fr.clementgre.pdf4teachers.panel.sidebar.texts.TreeViewSections.TextTreeSection;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.util.List;

// A comment of the evaluation (CommentBank entry) in the texts tab. Clicking it writes it on the copy; it is never linked to the written text.
public class EvaluationCommentItem extends TextTreeItem {

    private final CommentBank.Entry entry;

    public EvaluationCommentItem(CommentBank.Entry entry){
        super(getFont(entry.getStyle()), entry.getText(), getColor(entry.getStyle()), entry.getStyle() == null ? 0 : entry.getStyle().maxWidth(),
                TextTreeSection.EVAL_TYPE, entry.getUses(), entry.getCreated() / 1000);
        this.entry = entry;
        menu = buildMenu();
        Tooltip.install(pane, new Tooltip(TR.tr("textTab.evaluationList.uses", String.valueOf(entry.getUses()))
                + (entry.getUses() == 0 ? "" : "\n" + String.join(", ", entry.getCopies()))));
    }

    private static Font getFont(CommentBank.Style style){
        if(style == null) return FontUtils.getFont("Open Sans", false, false, 14);
        return FontUtils.getFont(style.font(), style.italic(), style.bold(), style.size());
    }
    private static Color getColor(CommentBank.Style style){
        try{
            return style == null ? Color.BLACK : Color.valueOf(style.color());
        }catch(IllegalArgumentException e){
            return Color.BLACK;
        }
    }

    public CommentBank.Entry getEntry(){
        return entry;
    }

    // A comment of the evaluation is a model: the written text must not modify it.
    @Override
    public TextElement addToDocument(boolean link, PageRenderer page, int x, int y, boolean centerOnY){
        return super.addToDocument(false, page, x, y, centerOnY);
    }

    private ContextMenu buildMenu(){
        MenuItem favorite = new MenuItem(TR.tr("elementMenu.addToFavouriteList"));
        favorite.setOnAction(e -> TextTreeView.addSavedElement(new TextTreeItem(getFont(), getText(), getColor(), getMaxWidth(),
                TextTreeSection.FAVORITE_TYPE, 0, System.currentTimeMillis() / 1000)));

        Menu move = new Menu(TR.tr("textTab.evaluationList.moveTo"));
        List<String> exercises = EvaluationComments.ExerciseContext.current().order();
        for(String exercise : exercises){
            MenuItem item = new MenuItem(exercise);
            item.setDisable(exercise.equals(entry.getExercise()));
            item.setOnAction(e -> moveTo(exercise));
            move.getItems().add(item);
        }
        if(!exercises.isEmpty()) move.getItems().add(new SeparatorMenuItem());
        MenuItem general = new MenuItem(TR.tr("textTab.evaluationList.general"));
        general.setDisable(entry.getExercise() == null);
        general.setOnAction(e -> moveTo(null));
        move.getItems().add(general);

        MenuItem remove = new MenuItem(TR.tr("textTab.evaluationList.remove"));
        remove.setOnAction(e -> {
            EvaluationComments.getBank().hide(entry);
            EvaluationComments.fireChanged(true);
        });
        MenuItem usages = new MenuItem(TR.tr("textTab.usages.menu"));
        usages.setOnAction(e -> new CommentUsagesWindow(getText()));
        return new ContextMenu(usages, favorite, move, new SeparatorMenuItem(), remove);
    }
    private void moveTo(String exercise){
        EvaluationComments.getBank().move(entry, exercise);
        EvaluationComments.fireChanged(true);
        if(MainWindow.textTab != null) MainWindow.textTab.treeView.evaluationSection.expand(exercise);
    }
}
