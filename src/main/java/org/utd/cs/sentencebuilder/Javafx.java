/**
 * JavaFX.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kalani Kawaguchi
 * Date: October 6 2025
 *
 * Description:
 * Simple UI with some placeholders.
 * Upload file button allows users to upload .txt files to be saved to the
 * data folder. Uploaded file will then be imported to the DB
 */
package org.utd.cs.sentencebuilder;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Javafx extends Application {

    private static DatabaseManager db; //Kevin Tran: Shared DatabaseManager instance
    public static void setDatabaseManager(DatabaseManager databaseManager) {
        db = databaseManager;
    }

    private static final int MAX_WORDS = 1;
    private static final FileChooser fileChooser = new FileChooser();

    @Override
    public void start(Stage stage) {
        // Upload File Row
        Button uploadButton = new Button("Upload a Text FIle");
        uploadButton.setOnAction(actionEvent -> {
            selectFile(stage);
        });

        // Top
        VBox inputRow = topRow();

        // Bottom
        TextArea output = bottomRow();

        // Main
        VBox root = new VBox(20, uploadButton, inputRow, output);
        root.setAlignment(Pos.CENTER);

        StackPane container = new StackPane(root);
        container.setAlignment(Pos.CENTER);

        Scene scene = new Scene(container, 640, 480);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    public static VBox topRow(){
        Label label = new Label("Enter a Starting Word:");
        TextField textField = new TextField();
        textField.setPrefWidth(150);
        textField.textProperty().addListener((observableValue, oldValue, newValue) -> {
            String[] words = newValue.trim().split("\\s+");
            if (words.length > MAX_WORDS){
                textField.setText(words[0]);
            }
        });

        HBox inputFields = new HBox(10, label, textField);
        inputFields.setAlignment(Pos.CENTER);

        Button button = new Button("Submit");

        VBox inputRow = new VBox(5, inputFields, button);
        inputRow.setAlignment(Pos.CENTER);

        return inputRow;
    }

    public static TextArea bottomRow(){
        TextArea output = new TextArea("Lorem ipsum dolor sit amet, consectetur");
        output.setEditable(false);
        output.setWrapText(true);
        output.setMaxWidth(300);
        output.setPrefHeight(300);
        
        return output;
    }

    public static void selectFile(Stage stage){
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        fileChooser.setTitle("Upload a .txt file");
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            Path dest = Path.of("data/clean", file.getName());

            try {
                Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("File saved");

                //Kevin Tran
                //Uses the shared DatabaseManager instance to import the file
                boolean wordsOnly = false;
                new ImporterCli(db).run(Path.of("data/clean"), wordsOnly);

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No file");
        }
    }
}