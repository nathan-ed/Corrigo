/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class ExercisePageMappingDialog {
    
    private final ExercisePageMapping exercisePageMapping;
    private final List<String> exerciseKeys;
    private final int pagesCount;
    private final Map<String, TextField> pageFields = new LinkedHashMap<>();
    
    public ExercisePageMappingDialog(ExercisePageMapping exercisePageMapping, List<String> exerciseKeys, int pagesCount){
        this.exercisePageMapping = exercisePageMapping;
        this.exerciseKeys = exerciseKeys;
        this.pagesCount = pagesCount;
    }
    
    public boolean show(){
        CustomAlert dialog = new CustomAlert(Alert.AlertType.CONFIRMATION, TR.tr("exercisePagesDialog.title"), TR.tr("exercisePagesDialog.title"));
        dialog.addOKButton(ButtonPosition.DEFAULT);
        dialog.addCancelButton(ButtonPosition.CLOSE);
        dialog.getDialogPane().setContent(buildContent());
        
        if(dialog.getShowAndWaitIsDefaultButton()){
            return apply();
        }
        return false;
    }
    
    private GridPane buildContent(){
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10, 0, 10, 0));
        
        grid.add(new Label(TR.tr("exercisePagesDialog.exercise")), 0, 0);
        grid.add(new Label(TR.tr("exercisePagesDialog.page")), 1, 0);
        
        int row = 1;
        for(String exerciseKey : exerciseKeys){
            TextField pageField = new TextField();
            pageField.setPrefColumnCount(4);
            pageField.setPromptText("1-" + pagesCount);
            
            OptionalInt pageIndex = exercisePageMapping.getPageIndex(exerciseKey);
            if(pageIndex.isPresent()){
                pageField.setText(String.valueOf(pageIndex.getAsInt() + 1));
            }
            
            pageFields.put(exerciseKey, pageField);
            grid.add(new Label(exerciseKey), 0, row);
            grid.add(pageField, 1, row);
            row++;
        }
        
        return grid;
    }
    
    private boolean apply(){
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for(Map.Entry<String, TextField> entry : pageFields.entrySet()){
            values.put(entry.getKey(), entry.getValue().getText());
        }
        try{
            applyValues(exercisePageMapping, values, pagesCount);
            return true;
        }catch(IllegalArgumentException e){
            MainWindow.footerBar.showToast(javafx.scene.paint.Color.web("#6a1b1b"), javafx.scene.paint.Color.WHITE,
                    TR.tr("footerBar.exercisePages.invalidPage", pagesCount));
            return false;
        }
    }
    
    static void applyValues(ExercisePageMapping exercisePageMapping, Map<String, String> pageValues, int pagesCount){
        LinkedHashMap<String, Integer> parsedValues = new LinkedHashMap<>();
        for(Map.Entry<String, String> entry : pageValues.entrySet()){
            String exerciseKey = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if(value.isEmpty()){
                parsedValues.put(exerciseKey, null);
                continue;
            }
            try{
                int oneBasedPage = Integer.parseInt(value);
                if(oneBasedPage < 1 || oneBasedPage > pagesCount){
                    throw new IllegalArgumentException("Page for " + exerciseKey + " must be between 1 and " + pagesCount + ".");
                }
                parsedValues.put(exerciseKey, oneBasedPage - 1);
            }catch(NumberFormatException e){
                throw new IllegalArgumentException("Page for " + exerciseKey + " must be a number.");
            }
        }
        for(Map.Entry<String, Integer> entry : parsedValues.entrySet()){
            if(entry.getValue() == null){
                exercisePageMapping.clearPageIndex(entry.getKey());
            }else{
                exercisePageMapping.setPageIndex(entry.getKey(), entry.getValue());
            }
        }
    }
}
