/**
 * JavaFX.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kalani Kawaguchi
 * Date: October 6, 2025
 *
 * Author: Taha Zaidi
 * Date: November 2 2025
 *
 * Author: Lavina Upendram
 * Date: November 18 2025
 *
 * Description:
 * JavaFX UI for the Sentence Builder project.
 */
package org.utd.cs.sentencebuilder;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.xml.transform.Source;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;

public class Javafx extends Application {

    private static DatabaseManager db; //Kevin Tran: Shared DatabaseManager instance
    public static void setDatabaseManager(DatabaseManager databaseManager) {
        db = databaseManager;
    }

    private static final int MAX_WORDS = 1;
    private static final FileChooser fileChooser = new FileChooser();
    //private static DatabaseManager db = new DatabaseManager();
    private static Scene homeScene;
    //private static Scene historyScene; // redundant, delete soon
    private static ObservableMap<String, SourceFile> importedFiles = FXCollections.observableHashMap();

    private Scene mainScene;
    private Scene historyScene;

    @Override
    public void start(Stage stage) {

        if (db == null) {
            System.out.println("DB was null — initializing new DatabaseManager()");
            db = new DatabaseManager();
        }

            stage.setTitle("Sentence Builder Project");

        // initialize both UI scenes
        mainScene = buildMainScene(stage);
        historyScene = buildHistoryScene(stage);

        stage.setScene(mainScene);
        stage.show();
    }

    // MAIN SCENE which includes the upload and generation
    private Scene buildMainScene(Stage stage) {
        // --- Upload Section ---
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

        VBox uploadBox = new VBox(8, uploadTitle, uploadButton);
        uploadBox.setAlignment(Pos.CENTER);

        // -- Starting Word Input ---
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

        // ---Algorithm Dropdown Selection ---
        Label algoLabel = new Label("Algorithm");
        algoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");

        ComboBox<String> algoDropdown = new ComboBox<>();
        algoDropdown.getItems().addAll("Greedy"); // add more if needed
        algoDropdown.setValue("Greedy");
        algoDropdown.setPrefWidth(180);

        HBox inputRow = new HBox(25,
                new VBox(5, startLabel, startInput),
                new VBox(5, algoLabel, algoDropdown)
        );
        inputRow.setAlignment(Pos.CENTER);

        // ---Generation & History Buttons---
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

        // ---Sentence Output Section--
        Label outputLabel = new Label("Generated Sentence");
        outputLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");

        TextArea outputArea = new TextArea("Your generated sentence will appear here...");
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setPrefWidth(400);
        outputArea.setPrefHeight(100);
        outputArea.setStyle(
                "-fx-font-style: italic;" +
                "-fx-text-fill: #4f5b4f;" +
                "-fx-font-size: 13px;" +
                "-fx-control-inner-background: #e4eddc;"
        );

        // ---Compose Card Layout---
        VBox card = new VBox(20, uploadBox, inputRow, generateButton, historyButton, new VBox(8, outputLabel, outputArea));
        card.setAlignment(Pos.CENTER);
        card.setStyle(
                "-fx-background-color: #ffffff;" +
                "-fx-background-radius: 20;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 4);"
        );
        card.setPadding(new javafx.geometry.Insets(30));

        StackPane container = new StackPane(card);
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-background-color: #f8fafb;");

        return new Scene(container, 600, 650);
    }

    // HISTORY SCENE of the upload records
    private Scene buildHistoryScene(Stage stage) {
        // Table for uploaded files
        TableView<SourceFile> importTable = new TableView<>();
        importTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SourceFile, String> fileNameCol = new TableColumn<>("File Name");
        fileNameCol.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().fileName()));

        TableColumn<SourceFile, Integer> wordCountCol = new TableColumn<>("Word Count");
        wordCountCol.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().wordCount()));

        TableColumn<SourceFile, Timestamp> timestampCol = new TableColumn<>("Import Time");
        timestampCol.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().importTimestamp()));

        importTable.getColumns().addAll(fileNameCol, wordCountCol, timestampCol);

        // Sort by timestamp descending
        timestampCol.setSortType(TableColumn.SortType.DESCENDING);
        importTable.getSortOrder().add(timestampCol);
        importTable.sort();

        // Bind table to importedFiles
        ObservableList<SourceFile> items = FXCollections.observableArrayList();
        importTable.setItems(items);

        importedFiles.addListener((MapChangeListener<String, SourceFile>) change -> {
            if (change.wasAdded()) {
                items.add(change.getValueAdded());
            }
        });

        // Load current files from DB
        try {
            Map<String, SourceFile> dbFiles = db.getAllSourceFiles();
            importedFiles.putAll(dbFiles);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Refresh thread to update table every 5 seconds
        Thread refresh = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    Map<String, SourceFile> updated = db.getAllSourceFiles();
                    Platform.runLater(() -> {
                        for (String key : updated.keySet()) {
                            if (!importedFiles.containsKey(key)) {
                                importedFiles.put(key, updated.get(key));
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        refresh.setDaemon(true);
        refresh.start();

        // Back button to return to main scene
        Button backButton = new Button("← Back");
        backButton.setOnAction(e -> stage.setScene(mainScene));
        backButton.setStyle(
                "-fx-background-color: #9bb0bb;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 8;"
        );

        // Layout for history scene
        VBox layout = new VBox(20, importTable, backButton);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 20;");
        layout.setPadding(new javafx.geometry.Insets(30));

        StackPane container = new StackPane(layout);
        container.setStyle("-fx-background-color: #f8fafb;");

        return new Scene(container, 600, 650);
    }

    public static void main(String[] args) {
        launch();
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