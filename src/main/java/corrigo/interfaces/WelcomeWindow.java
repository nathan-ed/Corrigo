/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.interfaces;

import corrigo.AppLinks;
import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.style.Style;
import fr.clementgre.pdf4teachers.utils.style.StyleManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * The first window of the application: Corrigo, and its language (French or English, the language of the computer
 * first). Written in both languages, since none is chosen yet.
 */
public final class WelcomeWindow extends Stage {

    private WelcomeWindow(Consumer<String> onChosen){
        setTitle(AppLinks.APP_NAME);
        getIcons().add(new Image(getClass().getResource("/logo.png") + ""));
        setResizable(false);

        ImageView logo = new ImageView(new Image(getClass().getResource("/logo.png") + ""));
        logo.setFitWidth(96);
        logo.setFitHeight(96);
        Label name = new Label(AppLinks.APP_NAME);
        name.setStyle("-fx-font-size: 30; -fx-font-weight: bold;");
        Label welcome = new Label("Bienvenue  ·  Welcome");
        welcome.setStyle("-fx-font-size: 16; -fx-opacity: .8;");
        Label choose = new Label("Choisissez la langue de l'application  ·  Choose the language of the application");
        choose.setStyle("-fx-font-size: 12; -fx-opacity: .7;");

        boolean french = "fr_fr".equals(TR.getLanguageFromComputerLanguage());
        Button fr = languageButton("Français", "Continuer en français", french);
        Button en = languageButton("English", "Continue in English", !french);
        fr.setOnAction(e -> choose(onChosen, "fr_fr"));
        en.setOnAction(e -> choose(onChosen, "en_us"));
        HBox buttons = french ? new HBox(16, fr, en) : new HBox(16, en, fr);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(10, logo, name, welcome, new Label(), choose, buttons);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(34, 40, 36, 40));
        Scene scene = new Scene(root);
        StyleManager.putStyle(scene, Style.DEFAULT);
        setScene(scene);
        setOnShown(e -> (french ? fr : en).requestFocus());
        // Closed without choosing: the language of the computer
        setOnCloseRequest(e -> choose(onChosen, french ? "fr_fr" : "en_us"));
    }

    private boolean chosen;
    private void choose(Consumer<String> onChosen, String language){
        if(chosen) return;
        chosen = true;
        close();
        onChosen.accept(language);
    }

    private static Button languageButton(String language, String action, boolean main){
        Label title = new Label(language);
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold;" + (main ? " -fx-text-fill: white;" : ""));
        Label detail = new Label(action);
        detail.setStyle("-fx-font-size: 12;" + (main ? " -fx-text-fill: #e6f0ff;" : " -fx-opacity: .75;"));
        VBox content = new VBox(4, title, detail);
        content.setAlignment(Pos.CENTER);
        Button button = new Button();
        button.setGraphic(content);
        button.setPrefSize(230, 90);
        button.setStyle(main ? "-fx-background-color: #2f7de1; -fx-background-radius: 8;"
                : "-fx-background-radius: 8; -fx-border-color: #6a7380; -fx-border-radius: 8;");
        button.setDefaultButton(main);
        return button;
    }

    // Asks the language, saves it, then runs next (the main window).
    public static void show(Runnable next){
        new WelcomeWindow(language -> {
            Main.settings.language.setValue(language);
            Main.settings.saveSettings();
            TR.updateLocale();
            next.run();
        }).show();
    }
}
