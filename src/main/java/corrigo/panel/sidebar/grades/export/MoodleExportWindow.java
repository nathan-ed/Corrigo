/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.export;

import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.render.export.ExportRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import corrigo.panel.sidebar.grades.export.MoodleFeedback.Match;
import corrigo.panel.sidebar.grades.export.MoodleFeedback.Participant;
import fr.clementgre.pdf4teachers.utils.dialogs.DialogBuilder;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.CustomAlert;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ErrorAlert;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.LoadingAlert;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zip of the graded copies of a folder, to upload in a Moodle assignment ("Upload multiple feedback files in a zip").
 * The teacher chooses the list of students (name → email) and the grading worksheet of the assignment (participant ids).
 */
public class MoodleExportWindow {

    private final File folder;
    private final List<File> copies;

    private final TextField studentsPath = new TextField();
    private final TextField worksheetPath = new TextField();
    private final ListView<String> preview = new ListView<>();
    private final Label summary = new Label();
    private List<Match> matches = List.of();

    // The graded copies (having an edition) of the files list that are in the folder of the open document.
    public MoodleExportWindow(){
        File current = MainWindow.mainScreen.hasDocument(false) ? MainWindow.mainScreen.document.getFile()
                : MainWindow.filesTab.getOpenedFiles().stream().findFirst().orElse(null);
        folder = current == null ? null : current.getParentFile();
        copies = MainWindow.filesTab.getOpenedFiles().stream()
                .filter(file -> folder != null && folder.equals(file.getParentFile()))
                .filter(file -> Edition.getEditFile(file).exists())
                .sorted()
                .toList();
    }

    public void show(){
        if(copies.isEmpty()){
            new ErrorAlert(TR.tr("moodleExport.noCopies"), null, false).showAndWait();
            return;
        }
        if(MainWindow.mainScreen.hasDocument(false) && !MainWindow.mainScreen.document.save(true)) return;

        CustomAlert dialog = new CustomAlert(Alert.AlertType.NONE, TR.tr("moodleExport.title"),
                TR.tr("moodleExport.header", String.valueOf(copies.size()), folder.getName()));

        studentsPath.setText(MainWindow.userData.lastMoodleStudentsFile);
        GridPane files = new GridPane();
        files.setHgap(8);
        files.setVgap(6);
        addFileRow(files, 0, TR.tr("moodleExport.students"), TR.tr("moodleExport.students.tooltip"), studentsPath);
        addFileRow(files, 1, TR.tr("moodleExport.worksheet"), TR.tr("moodleExport.worksheet.tooltip"), worksheetPath);

        preview.setPrefSize(640, 300);
        preview.setPlaceholder(new Label(TR.tr("moodleExport.choose")));
        summary.setWrapText(true);
        summary.setPrefWidth(640);
        summary.setMinHeight(Region.USE_PREF_SIZE);

        Hyperlink guide = new Hyperlink(TR.tr("moodleExport.guide"));
        guide.setOnAction(e -> TR.openUserGuide(TR.tr("moodleExport.guide.anchor")));

        VBox content = new VBox(10, guide, files, summary, preview);
        dialog.getDialogPane().setContent(content);
        dialog.addCancelButton(ButtonPosition.CLOSE);
        ButtonType create = dialog.addButton(TR.tr("moodleExport.create"), ButtonPosition.DEFAULT);
        Button createButton = (Button) dialog.getDialogPane().lookupButton(create);

        Runnable update = () -> {
            updatePreview();
            createButton.setDisable(matches.stream().noneMatch(Match::isOk));
        };
        studentsPath.textProperty().addListener((o, oldValue, newValue) -> update.run());
        worksheetPath.textProperty().addListener((o, oldValue, newValue) -> update.run());
        update.run();

        if(dialog.getShowAndWait() != create) return;
        MainWindow.userData.lastMoodleStudentsFile = studentsPath.getText();

        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(folder);
        chooser.setInitialFileName(folder.getName() + "_moodle.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        chooser.setTitle(TR.tr("dialog.file.saveFile.title"));
        File zip = chooser.showSaveDialog(Main.window);
        if(zip == null) return;
        if(!zip.getName().toLowerCase().endsWith(".zip")) zip = new File(zip.getAbsolutePath() + ".zip"); // macOS removes it
        createZip(matches.stream().filter(Match::isOk).toList(), zip);
    }

    private void addFileRow(GridPane grid, int row, String label, String tooltip, TextField path){
        Label title = new Label(label);
        title.setTooltip(new Tooltip(tooltip));
        path.setPrefColumnCount(40);
        GridPane.setHgrow(path, Priority.ALWAYS);
        Button browse = new Button(TR.tr("file.browse"));
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            File currentFile = new File(path.getText());
            File dir = currentFile.getParentFile() != null && currentFile.getParentFile().isDirectory() ? currentFile.getParentFile() : folder;
            chooser.setInitialDirectory(dir);
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV", "*.csv", "*.txt", "*.tsv"),
                    new FileChooser.ExtensionFilter(TR.tr("moodleExport.allFiles"), "*.*"));
            File file = chooser.showOpenDialog(browse.getScene().getWindow());
            if(file != null) path.setText(file.getAbsolutePath());
        });
        grid.addRow(row, title, path, browse);
        GridPane.setHalignment(title, javafx.geometry.HPos.LEFT);
        title.setAlignment(Pos.CENTER_LEFT);
    }

    // Reads both files and shows where each copy goes, and what is missing.
    private void updatePreview(){
        matches = List.of();
        preview.getItems().clear();
        File studentsFile = new File(studentsPath.getText().trim());
        File worksheetFile = new File(worksheetPath.getText().trim());
        if(!studentsFile.isFile() || !worksheetFile.isFile()){
            summary.setText(TR.tr("moodleExport.choose"));
            return;
        }
        List<MoodleFeedback.Student> students;
        List<Participant> participants;
        try{
            students = MoodleFeedback.parseStudents(MoodleFeedback.readText(studentsFile));
            participants = MoodleFeedback.parseWorksheet(MoodleFeedback.readText(worksheetFile));
        }catch(Exception e){
            Log.e(e);
            summary.setText(e.getMessage());
            return;
        }
        if(students.isEmpty()){
            summary.setText(TR.tr("moodleExport.noStudents"));
            return;
        }
        if(participants.isEmpty()){
            summary.setText(TR.tr("moodleExport.noParticipants"));
            return;
        }

        matches = MoodleFeedback.match(copies, students, participants);
        ArrayList<String> lines = new ArrayList<>();
        // Problems first
        for(Match match : matches){
            if(match.isOk()) continue;
            lines.add("⚠ " + match.copy().getName() + "  —  " + switch(match.problem()){
                case NOT_IN_STUDENTS -> TR.tr("moodleExport.problem.notInStudents", match.key());
                case AMBIGUOUS -> TR.tr("moodleExport.problem.ambiguous", match.key());
                case NOT_IN_WORKSHEET -> TR.tr("moodleExport.problem.notInWorksheet", match.student().email());
                case SAME_STUDENT -> TR.tr("moodleExport.problem.sameStudent", match.participant().fullName());
            });
        }
        for(Match match : matches){
            if(match.isOk()) lines.add("✓ " + match.copy().getName() + "  →  " + match.participant().fullName() + "  (" + match.participant().id() + ")");
        }
        List<Participant> withoutCopy = MoodleFeedback.getWithoutCopy(matches, participants);
        for(Participant participant : withoutCopy){
            lines.add("–  " + TR.tr("moodleExport.withoutCopy", participant.fullName()));
        }
        preview.getItems().setAll(lines);

        long ok = matches.stream().filter(Match::isOk).count();
        summary.setText(TR.tr("moodleExport.summary", (int) ok, copies.size(), (int) (copies.size() - ok), withoutCopy.size()));
    }

    // Renders each copy (all its edits, as "Export") into the zip, with the name Moodle expects.
    private void createZip(List<Match> toSend, File zip){
        LoadingAlert loading = new LoadingAlert(true, TR.tr("moodleExport.title"), TR.tr("dialogs.asyncAction.header"));
        loading.setTotal(toSend.size());
        boolean[] canceled = {false};
        loading.showAsync(() -> canceled[0] = true);
        int dpi = (int) MainWindow.userData.settingsExportImagesDPI;

        new Thread(() -> {
            ArrayList<String> errors = new ArrayList<>();
            int added = 0;
            try(ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))){
                for(Match match : toSend){
                    if(canceled[0]) break;
                    File rendered = Files.createTempFile("pdf4teachers-moodle", ".pdf").toFile();
                    try{
                        if(new ExportRenderer().exportFile(match.copy(), rendered, dpi, true, true, true, true, false)){
                            out.putNextEntry(new ZipEntry(MoodleFeedback.getZipEntryName(match.participant().fullName(), match.participant().id(), match.copy().getName())));
                            Files.copy(rendered.toPath(), out);
                            out.closeEntry();
                            added++;
                        }else errors.add(match.copy().getName());
                    }catch(Exception e){
                        Log.e(e);
                        errors.add(match.copy().getName() + " (" + e.getMessage() + ")");
                    }finally{
                        rendered.delete();
                    }
                    int progress = added + errors.size();
                    Platform.runLater(() -> {
                        loading.setCurrentTaskText(match.copy().getName());
                        loading.setProgress(progress);
                    });
                }
            }catch(Exception e){
                Log.e(e);
                errors.add(zip.getName() + " (" + e.getMessage() + ")");
            }
            int finalAdded = added;
            Platform.runLater(() -> {
                loading.close();
                if(canceled[0]){
                    zip.delete();
                    return;
                }
                String details = TR.tr("moodleExport.done.details")
                        + (errors.isEmpty() ? "" : "\n\n" + TR.tr("moodleExport.done.errors") + "\n" + String.join("\n", errors));
                DialogBuilder.showAlertWithOpenDirButton(TR.tr("moodleExport.title"), TR.tr("moodleExport.done.header", finalAdded), details, zip.getParentFile());
            });
        }, "Moodle export").start();
    }
}
