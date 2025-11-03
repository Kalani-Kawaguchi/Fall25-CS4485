/**
 * JavaFX.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kalani Kawaguchi
 * Date: October 6 2025
 *
 * Author: Taha Zaidi
 * Date: November 2 2025
 *
 * Description:
 * JavaFX UI for the Sentence Builder project.
 */

package org.utd.cs.sentencebuilder;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Javafx extends Application {
    private static final int MAX_WORDS = 1;
    private static final FileChooser fileChooser = new FileChooser();
    private final Label statusLabel = new Label();

    // Kevin Tran: Shared DatabaseManager instance
    private static DatabaseManager db;
    public static void setDatabaseManager(DatabaseManager databaseManager) {
        db = databaseManager;
    }

    // upload history data structure
    private static class UploadEntry {
        String fileName;
        long wordCount;
        String importTime;

        UploadEntry(String fileName, long wordCount, String importTime) {
            this.fileName = fileName;
            this.wordCount = wordCount;
            this.importTime = importTime;
        }
    }

    private final List<UploadEntry> uploadHistory = new ArrayList<>();
    private Scene mainScene;
    private Scene historyScene;

    // application entry
    @Override
    public void start(Stage stage) {
        stage.setTitle("Sentence Builder Project");

        // initialize both UI scenes
        mainScene = buildMainScene(stage);
        historyScene = buildHistoryScene(stage);

        stage.setScene(mainScene);
        stage.show();
    }

    // main scene which includes the upload and generation
    private Scene buildMainScene(Stage stage) {
        // upload section
        Label uploadTitle = new Label("Upload Text File");
        uploadTitle.setStyle(
                "-fx-font-family: 'Helvetica'; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 16px; " +
                        "-fx-text-fill: #333333;"
        );

        Button uploadButton = new Button("Click to upload a .txt file");
        uploadButton.setOnAction(actionEvent -> selectFile(stage));
        uploadButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-border-color: #cfcfcf;" +
                        "-fx-border-style: dashed;" +
                        "-fx-border-radius: 12;" +
                        "-fx-background-radius: 12;" +
                        "-fx-text-fill: #6b7580;" +
                        "-fx-font-size: 16px;" +
                        "-fx-padding: 50 100 50 100;"
        );

        VBox uploadBox = new VBox(8, uploadTitle, uploadButton, statusLabel);
        uploadBox.setAlignment(Pos.CENTER);

        // sentence input section
        Label startLabel = new Label("Starting Word");
        startLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");

        TextField startInput = new TextField();
        startInput.setPromptText("Enter a word to begin...");
        startInput.setPrefWidth(180);
        startInput.setStyle(
                "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-color: #d8dfe3;" +
                        "-fx-padding: 10 12 10 12;" +
                        "-fx-font-size: 13px;"
        );

        startInput.textProperty().addListener((observableValue, oldValue, newValue) -> {
            String[] words = newValue.trim().split("\\s+");
            if (words.length > MAX_WORDS) startInput.setText(words[0]);
        });

        // algorithm dropdown selection
        Label algoLabel = new Label("Algorithm");
        algoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");

        ComboBox<String> algoDropdown = new ComboBox<>();
        algoDropdown.getItems().addAll("Greedy");
        algoDropdown.setValue("Greedy");
        algoDropdown.setPrefWidth(180);

        HBox inputRow = new HBox(25, new VBox(5, startLabel, startInput), new VBox(5, algoLabel, algoDropdown));
        inputRow.setAlignment(Pos.CENTER);

        // generation and history buttons
        Button generateButton = new Button("Generate Sentence");
        generateButton.setStyle(
                "-fx-background-color: #9bb0bb;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-background-radius: 10;" +
                        "-fx-pref-width: 400;"
        );

        Button historyButton = new Button("View Upload History");
        historyButton.setOnAction(e -> stage.setScene(historyScene));
        historyButton.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-text-fill: #4a4f57;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-color: #d8dfe3;" +
                        "-fx-pref-width: 400;"
        );

        // sentence output section
        Label outputLabel = new Label("Generated Sentence");
        outputLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");

        TextArea output = new TextArea("Your generated sentence will appear here...");
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefWidth(400);
        output.setPrefHeight(100);
        output.setStyle(
                "-fx-font-style: italic;" +
                        "-fx-text-fill: #4f5b4f;" +
                        "-fx-font-size: 13px;" +
                        "-fx-control-inner-background: #e4eddc;"
        );

        // card layout
        VBox card = new VBox(20, uploadBox, inputRow, generateButton, historyButton, new VBox(8, outputLabel, output));
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30));
        card.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-background-radius: 20;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 4);"
        );

        StackPane container = new StackPane(card);
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-background-color: #f8fafb;");

        return new Scene(container, 600, 650);
    }

    // history scene of the upload records
    private Scene buildHistoryScene(Stage stage) {
        Label title = new Label("Upload History");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: #333333;");

        TableView<UploadEntry> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<UploadEntry, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().fileName));

        TableColumn<UploadEntry, String> wordCol = new TableColumn<>("Word Count");
        wordCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().wordCount)));

        TableColumn<UploadEntry, String> timeCol = new TableColumn<>("Import Time");
        timeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().importTime));

        tableView.getColumns().addAll(nameCol, wordCol, timeCol);

        // back button on history scene
        Button backButton = new Button("← Back");
        backButton.setOnAction(e -> stage.setScene(mainScene));
        backButton.setStyle(
                "-fx-background-color: #9bb0bb;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-background-radius: 8;"
        );

        VBox layout = new VBox(20, title, tableView, backButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-background-radius: 20;"
        );

        StackPane container = new StackPane(layout);
        container.setStyle("-fx-background-color: #f8fafb;");

        Scene scene = new Scene(container, 600, 650);

        // refresh the table when switched to this scene
        scene.windowProperty().addListener((obs, oldWin, newWin) -> {
            if (newWin != null) {
                tableView.getItems().setAll(uploadHistory);
            }
        });

        return scene;
    }

    // file upload logic
    public void selectFile(Stage stage) {
        fileChooser.getExtensionFilters().clear();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("Confirm Upload");
            confirmDialog.setHeaderText("Are you sure you want to upload this file?");
            confirmDialog.setContentText("File: " + file.getName() + "\nSize: " + file.length() / 1024 + " KB");

            Optional<ButtonType> result = confirmDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    Path dest = Path.of("data/clean", file.getName());
                    Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

                    long wordCount = countWordsInFile(file);
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    uploadHistory.add(new UploadEntry(file.getName(), wordCount, timestamp));

                    // Kevin Tran - Import into Database
                    boolean wordsOnly = false;
                    new ImporterCli(db).run(dest, wordsOnly);

                    statusLabel.setText("✅ Uploaded: " + file.getName() + " (" + wordCount + " words, " + timestamp + ")");
                } catch (IOException e) {
                    e.printStackTrace();
                    statusLabel.setText("❌ Error uploading file.");
                }
            } else {
                statusLabel.setText("Upload cancelled.");
            }
        } else {
            statusLabel.setText("No file selected.");
        }
    }

    // word count in file
    private long countWordsInFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines()
                    .flatMap(line -> Arrays.stream(line.trim().split("\\s+")))
                    .filter(word -> !word.isEmpty())
                    .count();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // main
    public static void main(String[] args) {
        launch();
    }
}
