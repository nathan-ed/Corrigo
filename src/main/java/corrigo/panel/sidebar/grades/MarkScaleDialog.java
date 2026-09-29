/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import corrigo.panel.sidebar.grades.MarkScale.Kind;
import corrigo.panel.sidebar.grades.MarkScale.Threshold;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeTreeView;
import fr.clementgre.pdf4teachers.utils.style.Style;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * The marks scale of the evaluation: linear (the Swiss 1–6 scale by default), a table of points and marks, or a
 * formula. A preview gives the marks for the total of the open grade scale.
 */
public final class MarkScaleDialog {

    private static final double[] STEPS = {1, .5, .25, .1, 0};

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final ToggleGroup kind = new ToggleGroup();
    private final RadioButton linear = new RadioButton(), table = new RadioButton(), formula = new RadioButton();
    private final TextField min = new TextField(), max = new TextField(), formulaField = new TextField();
    private final TextArea tableArea = new TextArea();
    private final ComboBox<Double> step = new ComboBox<>();
    private final Label description = new Label(), error = new Label();
    private final GridPane preview = new GridPane();
    private ButtonType ok;

    private MarkScaleDialog(){
    }

    public static void show(){
        new MarkScaleDialog().open();
    }

    // "Linear: points / total × 5 + 1, rounded to 0.5, from 1 to 6"
    public static String describe(MarkScale scale){
        NumberFormat f = MainWindow.gradesDigFormat;
        String rounding = scale.getStep() <= 0 ? TR.tr("markScale.rounding.none") : TR.tr("markScale.rounding.to", f.format(scale.getStep()));
        String limits = TR.tr("markScale.limits", f.format(scale.getMin()), f.format(scale.getMax()));
        return switch(scale.getKind()){
            case LINEAR -> TR.tr("markScale.describe.linear", f.format(scale.getMax() - scale.getMin()), f.format(scale.getMin())) + ", " + rounding + ", " + limits;
            case TABLE -> TR.tr("markScale.describe.table", String.valueOf(scale.getTable().size())) + ", " + limits;
            case FORMULA -> TR.tr("markScale.describe.formula", scale.getFormula()) + ", " + rounding + ", " + limits;
        };
    }

    private void open(){
        MarkScale scale = Marks.getScale();
        NumberFormat f = MainWindow.gradesDigFormat;
        dialog.initOwner(MainWindow.mainScreen.getScene().getWindow());
        dialog.setTitle(TR.tr("markScale.title"));
        dialog.setHeaderText(TR.tr("markScale.header"));

        linear.setText(TR.tr("markScale.linear"));
        table.setText(TR.tr("markScale.table"));
        formula.setText(TR.tr("markScale.formula"));
        for(RadioButton button : new RadioButton[]{linear, table, formula}) button.setToggleGroup(kind);
        (switch(scale.getKind()){ case LINEAR -> linear; case TABLE -> table; case FORMULA -> formula; }).setSelected(true);

        min.setText(f.format(scale.getMin()));
        max.setText(f.format(scale.getMax()));
        for(TextField field : new TextField[]{min, max}) field.setPrefColumnCount(4);
        step.getItems().addAll(1d, .5, .25, .1, 0d);
        step.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Double value){
                return value == null ? "" : value == 0 ? TR.tr("markScale.rounding.none") : f.format(value);
            }
            @Override public Double fromString(String text){ return 0d; }
        });
        step.setValue(closestStep(scale.getStep()));
        HBox limits = new HBox(8, new Label(TR.tr("markScale.min")), min, new Label(TR.tr("markScale.max")), max,
                new Label("   " + TR.tr("markScale.rounding")), step);
        limits.setAlignment(Pos.CENTER_LEFT);

        StringBuilder rows = new StringBuilder();
        for(Threshold threshold : scale.getTable()) rows.append(f.format(threshold.points())).append("  ").append(f.format(threshold.mark())).append('\n');
        tableArea.setText(rows.toString());
        tableArea.setPromptText(TR.tr("markScale.table.prompt"));
        tableArea.setPrefRowCount(6);
        tableArea.setPrefColumnCount(18);
        Label tableHelp = muted(TR.tr("markScale.table.help"));
        VBox tableBox = new VBox(4, tableHelp, tableArea);
        tableBox.setPadding(new Insets(0, 0, 0, 26));

        formulaField.setText(scale.getFormula());
        formulaField.setPromptText("p / t * 5 + 1");
        Label formulaHelp = muted(TR.tr("markScale.formula.help"));
        VBox formulaBox = new VBox(4, formulaField, formulaHelp);
        formulaBox.setPadding(new Insets(0, 0, 0, 26));

        Label linearHelp = muted(TR.tr("markScale.linear.help"));
        linearHelp.setPadding(new Insets(0, 0, 0, 26));
        // Usual scales, in one click
        HBox presets = new HBox(8, new Label(TR.tr("markScale.presets")),
                preset(TR.tr("markScale.swiss"), 1, 6, .5), preset(TR.tr("markScale.on20"), 0, 20, .5), preset(TR.tr("markScale.on10"), 0, 10, .5));
        presets.setAlignment(Pos.CENTER_LEFT);

        description.setWrapText(true);
        description.setStyle("-fx-font-weight: bold;");
        error.setStyle("-fx-text-fill: #e57373;");
        error.setWrapText(true);
        error.managedProperty().bind(error.visibleProperty());
        preview.setHgap(14);
        preview.setVgap(2);

        VBox content = new VBox(10, presets, new Separator(), linear, linearHelp, table, tableBox, formula, formulaBox, new Separator(), limits,
                new Separator(), description, error, new Label(TR.tr("markScale.preview", f.format(getTotal()))), preview);
        content.setPadding(new Insets(12));
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);
        ok = new ButtonType(TR.tr("actions.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(TR.tr("actions.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, ok);
        dialog.setResizable(true);
        StyleManager.putStyle(dialog.getDialogPane(), Style.DEFAULT);

        kind.selectedToggleProperty().addListener((o, oldValue, newValue) -> {
            if(newValue == null) kind.selectToggle(oldValue);
            update();
        });
        for(TextInputControl field : new TextInputControl[]{min, max, formulaField, tableArea}) field.textProperty().addListener((o, a, b) -> update());
        step.valueProperty().addListener((o, a, b) -> update());
        update();

        if(dialog.showAndWait().orElse(cancel) == ok) Marks.changeScale(read());
    }

    private void update(){
        tableArea.setDisable(!table.isSelected());
        formulaField.setDisable(!formula.isSelected());
        MarkScale scale = read();
        String problem = scale == null ? TR.tr("markScale.error.limits") : scale.getError() == null ? null
                : TR.tr("markScale.error", scale.getError());
        error.setText(problem == null ? "" : problem);
        error.setVisible(problem != null);
        dialog.getDialogPane().lookupButton(ok).setDisable(problem != null);
        preview.getChildren().clear();
        if(problem != null){
            description.setText("");
            return;
        }
        description.setText(describe(scale));
        // The marks for some points of the total
        NumberFormat f = MainWindow.gradesDigFormat;
        double total = getTotal();
        List<Double> points = new ArrayList<>();
        for(int i = 0; i <= 10; i++) points.add(Math.round(total * i / 10 * 2) / 2d);
        for(int i = 0; i < points.size(); i++){
            Label p = new Label(f.format(points.get(i)));
            Label m = new Label(f.format(scale.compute(points.get(i), total)));
            p.setStyle("-fx-opacity: .7;");
            m.setStyle("-fx-font-weight: bold;");
            preview.add(p, i, 0);
            preview.add(m, i, 1);
        }
    }

    private Button preset(String name, double minValue, double maxValue, double stepValue){
        Button button = new Button(name);
        button.setOnAction(e -> {
            linear.setSelected(true);
            min.setText(MainWindow.gradesDigFormat.format(minValue));
            max.setText(MainWindow.gradesDigFormat.format(maxValue));
            step.setValue(stepValue);
        });
        return button;
    }

    // The scale typed, or null if its limits are not numbers
    private MarkScale read(){
        Double minValue = parse(min.getText()), maxValue = parse(max.getText());
        if(minValue == null || maxValue == null || maxValue <= minValue) return null;
        Kind k = table.isSelected() ? Kind.TABLE : formula.isSelected() ? Kind.FORMULA : Kind.LINEAR;
        ArrayList<Threshold> thresholds = new ArrayList<>();
        for(String line : tableArea.getText().split("\\R")){
            String[] parts = line.strip().split("[\\s;:→>=]+");
            if(parts.length < 2) continue;
            Double points = parse(parts[0]), mark = parse(parts[parts.length - 1]);
            if(points != null && mark != null) thresholds.add(new Threshold(points, mark));
        }
        return new MarkScale(k, minValue, maxValue, step.getValue() == null ? .5 : step.getValue(), thresholds, formulaField.getText());
    }

    private static double getTotal(){
        return GradeTreeView.getTotal() != null && GradeTreeView.getTotal().getCore().getTotal() > 0 ? GradeTreeView.getTotal().getCore().getTotal() : 20;
    }
    private static double closestStep(double value){
        for(double s : STEPS) if(Math.abs(s - value) < 1e-9) return s;
        return .5;
    }
    private static Double parse(String text){
        try{
            return Double.parseDouble(text.strip().replace(',', '.'));
        }catch(NumberFormatException e){
            return null;
        }
    }
    private static Label muted(String text){
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-opacity: .7; -fx-font-size: 11;");
        return label;
    }
}
