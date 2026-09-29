/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades;

import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.Element;
import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.NewEvaluationPlan.Copy;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.NewEvaluationPlan.Problem;
import fr.clementgre.pdf4teachers.utils.PlatformUtils;
import fr.clementgre.pdf4teachers.utils.dialogs.FilesChooserManager;
import fr.clementgre.pdf4teachers.utils.style.Style;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.multipdf.PageExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A new evaluation from the scan of all its copies: the scanned PDF, the students in the order of the scan, the pages of
 * each copy (checked on the first page of each one), then the grade scale: created with the assistant, imported (an
 * exported grade scale, or a copy of another evaluation), or later. The copies are written in the folder of the
 * evaluation ("07_DUPONT.pdf") and opened.
 */
public final class NewEvaluationWizard {

    private enum ScaleChoice { CREATE, IMPORT, LATER }

    // The top of the first page of each copy, where the name is written, large enough to be read
    private static final double THUMBNAIL_DPI = 80, THUMBNAIL_TOP = .33;
    private static final int THUMBNAIL_WIDTH = 330;

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final StackPane stepPane = new StackPane();
    private final Label stepTitle = new Label();
    private final Label error = new Label();
    private ButtonType back, next;
    private int step;

    // Step 1: the scan and the folder
    private File scan;
    private int scanPages;
    private final Label scanLabel = new Label();
    private final TextField folder = new TextField();
    private final Label folderWarning = new Label();
    // Step 2: the students
    private final TextArea students = new TextArea();
    private final Label studentsCount = new Label();
    private final CheckBox numbered = new CheckBox();
    private final CheckBox upperCase = new CheckBox();
    // Step 3: the pages of each copy
    private final Spinner<Integer> pagesPerCopy = new Spinner<>(1, 200, 1);
    private final Label pagesInfo = new Label();
    private final FlowPane thumbnails = new FlowPane(10, 10);
    private PDDocument scanDocument;
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "New evaluation previews");
        thread.setDaemon(true);
        return thread;
    });
    private int renderGeneration;
    // Step 4: the grade scale
    private final ToggleGroup scaleChoice = new ToggleGroup();
    private final RadioButton createScale = new RadioButton();
    private final RadioButton importScale = new RadioButton();
    private final RadioButton laterScale = new RadioButton();
    private File importedScale;
    private final Label importedLabel = new Label();
    private final ProgressBar progress = new ProgressBar(0);

    private NewEvaluationWizard(){
    }

    public static void show(){
        new NewEvaluationWizard().open();
    }

    private void open(){
        dialog.initOwner(MainWindow.mainScreen.getScene().getWindow());
        dialog.setTitle(TR.tr("newEvaluation.title"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        stepTitle.setStyle("-fx-font-size: 17; -fx-font-weight: bold;");
        error.setWrapText(true);
        error.setStyle("-fx-text-fill: #e57373;");
        error.managedProperty().bind(error.visibleProperty());
        VBox content = new VBox(12, stepTitle, stepPane, error);
        content.setPadding(new Insets(14));
        content.setPrefSize(760, 560);
        VBox.setVgrow(stepPane, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);

        back = new ButtonType(TR.tr("newEvaluation.back"), ButtonBar.ButtonData.BACK_PREVIOUS);
        next = new ButtonType(TR.tr("newEvaluation.next"), ButtonBar.ButtonData.NEXT_FORWARD);
        ButtonType cancel = new ButtonType(TR.tr("actions.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, back, next);
        StyleManager.putStyle(dialog.getDialogPane(), Style.DEFAULT);
        // Back and next change the step: the dialog closes only when the evaluation is created
        dialog.getDialogPane().lookupButton(back).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            showStep(step - 1);
        });
        dialog.getDialogPane().lookupButton(next).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            if(step < 3) showStep(step + 1);
            else create();
        });

        setupScanStep();
        setupStudentsStep();
        setupPagesStep();
        setupScaleStep();
        showStep(0);

        dialog.showAndWait();
        renderer.shutdownNow();
        closeScan();
    }

    // STEPS

    private final List<Node> steps = new ArrayList<>();

    private void showStep(int index){
        step = Math.clamp(index, 0, steps.size() - 1);
        stepPane.getChildren().setAll(steps.get(step));
        stepTitle.setText(TR.tr("newEvaluation.step" + (step + 1)));
        dialog.getDialogPane().lookupButton(back).setDisable(step == 0);
        ((Button) dialog.getDialogPane().lookupButton(next)).setText(TR.tr(step == 3 ? "newEvaluation.create" : "newEvaluation.next"));
        if(step == 2) updatePages(true);
        validate();
    }

    private void validate(){
        if(step == 0) updateFolderWarning();
        String problem = switch(step){
            case 0 -> scan == null ? TR.tr("newEvaluation.error.noScan") : folder.getText().isBlank() ? TR.tr("newEvaluation.error.noFolder") : null;
            case 1 -> getStudents().isEmpty() ? TR.tr("newEvaluation.error.noStudent") : null;
            case 2 -> {
                Problem check = NewEvaluationPlan.check(getPlan(), scanPages);
                yield NewEvaluationPlan.isBlocking(check) ? TR.tr("newEvaluation.error." + check.name().toLowerCase()) : null;
            }
            default -> getScaleChoice() == ScaleChoice.IMPORT && importedScale == null ? TR.tr("newEvaluation.error.noImportedScale") : null;
        };
        error.setText(problem == null ? "" : problem);
        error.setVisible(problem != null);
        dialog.getDialogPane().lookupButton(next).setDisable(problem != null);
    }


    // 1. THE SCAN

    private void setupScanStep(){
        Label intro = wrapped(TR.tr("newEvaluation.scan.intro"));
        Button choose = new Button(TR.tr("newEvaluation.scan.choose"));
        choose.setOnAction(e -> chooseScan());
        scanLabel.setText(TR.tr("newEvaluation.scan.none"));
        HBox scanBox = new HBox(10, choose, scanLabel);
        scanBox.setAlignment(Pos.CENTER_LEFT);

        Label folderLabel = new Label(TR.tr("newEvaluation.folder"));
        folder.setPromptText(TR.tr("newEvaluation.folder.prompt"));
        HBox.setHgrow(folder, Priority.ALWAYS);
        folder.textProperty().addListener((o, oldValue, newValue) -> validate());
        Button changeFolder = new Button(TR.tr("newEvaluation.folder.change"));
        changeFolder.setOnAction(e -> {
            File chosen = FilesChooserManager.showDirectoryDialog(FilesChooserManager.SyncVar.LAST_OPEN_DIR);
            if(chosen != null) folder.setText(chosen.getAbsolutePath());
        });
        HBox folderBox = new HBox(8, folder, changeFolder);
        folderBox.setAlignment(Pos.CENTER_LEFT);
        Label folderHelp = muted(TR.tr("newEvaluation.folder.help"));

        folderWarning.setWrapText(true);
        folderWarning.setStyle("-fx-text-fill: #ffb74d;");
        folderWarning.managedProperty().bind(folderWarning.visibleProperty());
        folderWarning.setVisible(false);
        steps.add(new VBox(12, intro, scanBox, new Separator(), folderLabel, folderBox, folderWarning, folderHelp));
    }

    // The folder already has PDF files: another evaluation, whose copies are never replaced
    private void updateFolderWarning(){
        if(folder.getText().isBlank()){
            folderWarning.setVisible(false);
            return;
        }
        File[] pdfs = new File(folder.getText().strip()).listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        folderWarning.setVisible(pdfs != null && pdfs.length > 0);
        if(pdfs != null && pdfs.length > 0) folderWarning.setText(TR.tr("newEvaluation.folder.notEmpty", String.valueOf(pdfs.length)));
    }

    private void chooseScan(){
        File chosen = FilesChooserManager.showPDFFileDialog(FilesChooserManager.SyncVar.LAST_OPEN_DIR);
        if(chosen == null) return;
        closeScan();
        try{
            scanDocument = Loader.loadPDF(new RandomAccessReadBufferedFile(chosen));
        }catch(Exception e){
            Log.e("Unable to read the scan " + chosen + ": " + e.getMessage());
            scan = null;
            scanLabel.setText(TR.tr("newEvaluation.scan.unreadable", chosen.getName()));
            validate();
            return;
        }
        scan = chosen;
        scanPages = scanDocument.getNumberOfPages();
        scanLabel.setText(TR.tr("newEvaluation.scan.chosen", chosen.getName(), String.valueOf(scanPages)));
        // The copies go in a folder named after the scan, next to it
        String baseName = chosen.getName().replaceFirst("(?i)\\.pdf$", "");
        folder.setText(new File(chosen.getParentFile(), baseName).getAbsolutePath());
        validate();
    }

    private void closeScan(){
        renderGeneration++;
        if(scanDocument == null) return;
        PDDocument document = scanDocument;
        scanDocument = null;
        Runnable close = () -> {
            try{
                document.close();
            }catch(Exception ignored){
            }
        };
        if(renderer.isShutdown()) close.run();
        else renderer.execute(close); // After the previews being rendered
    }

    // 2. THE STUDENTS

    private void setupStudentsStep(){
        Label intro = wrapped(TR.tr("newEvaluation.students.intro"));
        students.setPromptText(TR.tr("newEvaluation.students.prompt"));
        students.setPrefRowCount(14);
        VBox.setVgrow(students, Priority.ALWAYS);
        students.textProperty().addListener((o, oldValue, newValue) -> {
            studentsCount.setText(TR.tr("newEvaluation.students.count", String.valueOf(getStudents().size())));
            validate();
        });
        studentsCount.setText(TR.tr("newEvaluation.students.count", "0"));
        numbered.setText(TR.tr("newEvaluation.students.numbered"));
        numbered.setSelected(true);
        upperCase.setText(TR.tr("newEvaluation.students.upperCase"));
        upperCase.setSelected(true);
        HBox options = new HBox(20, studentsCount, numbered, upperCase);
        options.setAlignment(Pos.CENTER_LEFT);
        steps.add(new VBox(10, intro, students, options));
    }

    private List<String> getStudents(){
        return NewEvaluationPlan.parseStudents(students.getText());
    }

    // 3. THE PAGES OF EACH COPY

    private void setupPagesStep(){
        Label intro = wrapped(TR.tr("newEvaluation.pages.intro"));
        Label pagesLabel = new Label(TR.tr("newEvaluation.pages.perCopy"));
        pagesPerCopy.setEditable(true);
        pagesPerCopy.setPrefWidth(80);
        pagesPerCopy.valueProperty().addListener((o, oldValue, newValue) -> updatePages(false));
        HBox pagesBox = new HBox(10, pagesLabel, pagesPerCopy, pagesInfo);
        pagesBox.setAlignment(Pos.CENTER_LEFT);
        pagesInfo.setWrapText(true);
        ScrollPane scroll = new ScrollPane(thumbnails);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        thumbnails.setPadding(new Insets(6));
        steps.add(new VBox(10, intro, pagesBox, scroll));
    }

    private int guessedFor = -1;
    // Shows the first page of each copy with its file name: the teacher checks the names against the copies
    private void updatePages(boolean guess){
        List<String> list = getStudents();
        if(guess && guessedFor != list.size() * 10000 + scanPages){ // Guessed again when the students or the scan change
            guessedFor = list.size() * 10000 + scanPages;
            pagesPerCopy.getValueFactory().setValue(Math.max(1, NewEvaluationPlan.guessPagesPerCopy(scanPages, list.size())));
        }
        List<Copy> plan = getPlan();
        Problem problem = NewEvaluationPlan.check(plan, scanPages);
        int used = plan.isEmpty() ? 0 : plan.getLast().lastPage() + 1;
        String info = TR.tr("newEvaluation.pages.info", String.valueOf(plan.size()), String.valueOf(pagesPerCopy.getValue()), String.valueOf(used), String.valueOf(scanPages));
        if(problem == Problem.EXTRA_PAGES) info += "  " + TR.tr("newEvaluation.pages.extra", String.valueOf(scanPages - used));
        pagesInfo.setText(info);
        pagesInfo.setStyle(problem == null ? "-fx-text-fill: #81c784;" : NewEvaluationPlan.isBlocking(problem) ? "-fx-text-fill: #e57373;" : "-fx-text-fill: #ffb74d;");

        thumbnails.getChildren().clear();
        int generation = ++renderGeneration;
        PDDocument document = scanDocument;
        for(Copy copy : plan){
            StackPane image = new StackPane(new ProgressIndicator());
            image.setPrefSize(THUMBNAIL_WIDTH, THUMBNAIL_WIDTH * THUMBNAIL_TOP * 1.414);
            image.setStyle("-fx-background-color: rgba(128,128,128,.15);");
            Label name = new Label(copy.fileName());
            name.setMaxWidth(THUMBNAIL_WIDTH);
            name.setStyle("-fx-font-size: 11;");
            Label pages = muted(TR.tr("newEvaluation.pages.range", String.valueOf(copy.firstPage() + 1), String.valueOf(copy.lastPage() + 1)));
            VBox card = new VBox(3, image, name, pages);
            card.setAlignment(Pos.TOP_CENTER);
            thumbnails.getChildren().add(card);
            if(document == null || copy.firstPage() >= scanPages) continue;
            renderer.execute(() -> {
                if(generation != renderGeneration) return; // Replaced by newer previews
                try{
                    BufferedImage page = new PDFRenderer(document).renderImageWithDPI(copy.firstPage(), (float) THUMBNAIL_DPI);
                    BufferedImage rendered = page.getSubimage(0, 0, page.getWidth(), (int) Math.max(1, page.getHeight() * THUMBNAIL_TOP));
                    Platform.runLater(() -> {
                        ImageView view = new ImageView(SwingFXUtils.toFXImage(rendered, null));
                        view.setPreserveRatio(true);
                        view.setSmooth(true);
                        view.setFitWidth(THUMBNAIL_WIDTH);
                        image.getChildren().setAll(view);
                    });
                }catch(Exception e){
                    Platform.runLater(() -> image.getChildren().setAll(muted("?")));
                }
            });
        }
        validate();
    }

    private List<Copy> getPlan(){
        return NewEvaluationPlan.plan(getStudents(), pagesPerCopy.getValue(), numbered.isSelected(), upperCase.isSelected());
    }

    // 4. THE GRADE SCALE

    private void setupScaleStep(){
        Label intro = wrapped(TR.tr("newEvaluation.scale.intro"));
        createScale.setText(TR.tr("newEvaluation.scale.create"));
        importScale.setText(TR.tr("newEvaluation.scale.import"));
        laterScale.setText(TR.tr("newEvaluation.scale.later"));
        for(RadioButton button : new RadioButton[]{createScale, importScale, laterScale}){
            button.setToggleGroup(scaleChoice);
            button.setWrapText(true);
        }
        createScale.setSelected(true);
        scaleChoice.selectedToggleProperty().addListener((o, oldValue, newValue) -> {
            if(newValue == null) scaleChoice.selectToggle(oldValue);
            validate();
        });
        Button chooseImport = new Button(TR.tr("newEvaluation.scale.import.choose"));
        chooseImport.disableProperty().bind(importScale.selectedProperty().not());
        chooseImport.setOnAction(e -> {
            File chosen = FilesChooserManager.showFileDialog(FilesChooserManager.SyncVar.LAST_OPEN_DIR, TR.tr("newEvaluation.scale.import.types"), "*.yml", "*.pdf");
            if(chosen == null) return;
            if(loadScale(chosen).isEmpty()){
                importedScale = null;
                importedLabel.setText(TR.tr("newEvaluation.scale.import.empty", chosen.getName()));
            }else{
                importedScale = chosen;
                importedLabel.setText(chosen.getName());
            }
            validate();
        });
        HBox importBox = new HBox(10, chooseImport, importedLabel);
        importBox.setAlignment(Pos.CENTER_LEFT);
        importBox.setPadding(new Insets(0, 0, 0, 26));
        Label importHelp = muted(TR.tr("newEvaluation.scale.import.help"));
        importHelp.setPadding(new Insets(0, 0, 0, 26));
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        steps.add(new VBox(12, intro, createScale, importScale, importBox, importHelp, laterScale, new Region(), progress));
    }

    private ScaleChoice getScaleChoice(){
        return importScale.isSelected() ? ScaleChoice.IMPORT : laterScale.isSelected() ? ScaleChoice.LATER : ScaleChoice.CREATE;
    }

    // The grades of an exported grade scale (.yml), or of a copy of another evaluation (.pdf, its edition)
    private static List<GradeRating> loadScale(File file){
        ArrayList<GradeRating> ratings = new ArrayList<>();
        File editFile = file.getName().toLowerCase().endsWith(".pdf") ? Edition.getEditFile(file) : file;
        if(editFile == null || !editFile.exists()) return ratings;
        try{
            for(Element element : Edition.simpleLoad(editFile)){
                if(element instanceof GradeElement grade) ratings.add(grade.toGradeRating());
            }
        }catch(Exception e){
            Log.e("Unable to read the grade scale of " + file + ": " + e.getMessage());
        }
        return ratings;
    }

    // CREATION

    private void create(){
        File directory = new File(folder.getText().strip());
        List<Copy> plan = getPlan();
        List<File> files = plan.stream().map(copy -> new File(directory, copy.fileName())).toList();
        // Never written over: the copies of another evaluation keep their annotations, comments, methods and mistakes
        long existing = files.stream().filter(File::exists).count();
        if(existing > 0){
            error.setText(TR.tr("newEvaluation.error.existing", String.valueOf(existing), directory.getName()));
            error.setVisible(true);
            return;
        }

        ScaleChoice choice = getScaleChoice();
        List<GradeRating> ratings = choice == ScaleChoice.IMPORT ? loadScale(importedScale) : List.of();
        PDDocument document = scanDocument;
        for(ButtonType button : dialog.getDialogPane().getButtonTypes()) dialog.getDialogPane().lookupButton(button).setDisable(true);
        progress.setVisible(true);

        renderGeneration++; // Stops the previews
        renderer.execute(() -> {
            String failure = null;
            try{
                directory.mkdirs();
                for(int i = 0; i < plan.size(); i++){
                    Copy copy = plan.get(i);
                    try(PDDocument extracted = new PageExtractor(document, copy.firstPage() + 1, copy.lastPage() + 1).extract()){
                        extracted.save(files.get(i));
                    }
                    double done = (i + 1d) / plan.size();
                    Platform.runLater(() -> progress.setProgress(done));
                }
            }catch(Exception e){
                Log.e(e);
                failure = e.getMessage();
            }
            String finalFailure = failure;
            Platform.runLater(() -> {
                if(finalFailure != null){
                    for(ButtonType button : dialog.getDialogPane().getButtonTypes()) dialog.getDialogPane().lookupButton(button).setDisable(false);
                    progress.setVisible(false);
                    error.setText(TR.tr("newEvaluation.error.write", finalFailure));
                    error.setVisible(true);
                    return;
                }
                if(!ratings.isEmpty()) writeScale(ratings, files);
                dialog.setResult(next);
                dialog.close();
                openEvaluation(files, choice == ScaleChoice.CREATE);
            });
        });
    }

    // The imported grade scale in each new copy, with its positions
    private static void writeScale(List<GradeRating> ratings, List<File> files){
        GradeCopyGradeScaleDialog copier = new GradeCopyGradeScaleDialog();
        copier.ratings.addAll(ratings);
        for(File file : files) copier.copyToFile(file, true, true);
    }

    // The copies in the files list, the first one open in the grading panel, then the grade scale assistant
    private static void openEvaluation(List<File> files, boolean createScale){
        MainWindow.filesTab.clearFiles();
        MainWindow.filesTab.openFiles(files, false);
        MainWindow.mainScreen.openFile(files.getFirst());
        MainWindow.gradingTab.select();
        whenOpen(files.getFirst(), 40, () -> {
            if(createScale || GradeTreeView.getTotal().getChildren().isEmpty()){
                if(createScale) GradeScaleSetupDialog.show();
                return;
            }
            // Grading starts with the first exercise, at its page
            MainWindow.footerBar.refreshExerciseChoices();
            MainWindow.footerBar.setSelectedExerciseKey(MainWindow.footerBar.getExerciseKey(0));
            MainWindow.footerBar.navigateToSelectedExercisePage();
        });
    }
    private static void whenOpen(File file, int retries, Runnable action){
        PlatformUtils.runLaterOnUIThread(250, () -> {
            boolean open = MainWindow.mainScreen.hasDocument(false) && MainWindow.mainScreen.document.getFile().equals(file)
                    && GradeTreeView.getTotal() != null;
            if(open) action.run();
            else if(retries > 0) whenOpen(file, retries - 1, action);
        });
    }

    private static Label wrapped(String text){
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
    private static Label muted(String text){
        Label label = wrapped(text);
        label.setStyle("-fx-opacity: .7; -fx-font-size: 11;");
        return label;
    }
}
