/**
 * JavaFX.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kalani Kawaguchi
 * Date: October 6, 2025
 *
 * Description:
 * Simple UI with some placeholders.
 * Upload file button allows users to upload .txt files to be saved to the
 * data folder. Uploaded file will then be imported to the DB
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
    private static Scene homeScene;
    private static Scene historyScene;
    private static ObservableMap<String, SourceFile> importedFiles = FXCollections.observableHashMap();

    @Override
    public void start(Stage stage) {

        // Home Scene
        createHomeScene(stage);

        // Scene 2
        createHistoryScene(stage);

        stage.setScene(homeScene);
        stage.setTitle("Sentence Builder");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    public static VBox inputRow(){
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

    public static TextArea outputRow(){
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

    public static void createHomeScene(Stage stage){
        // Upload File Row
        Button uploadButton = new Button("Upload a Text FIle");
        uploadButton.setOnAction(actionEvent -> {
            selectFile(stage);
        });

        // Input Row
        VBox inputRow = inputRow();

        // Output Row
        TextArea output = outputRow();

        // Swap Scene Button
        Button toScene2Button = new Button("To Upload History");
        toScene2Button.setOnAction(e -> stage.setScene(historyScene));

        // Main
        VBox root = new VBox(20, uploadButton, inputRow, output, toScene2Button);
        root.setAlignment(Pos.CENTER);

        StackPane container = new StackPane(root);
        container.setAlignment(Pos.CENTER);

        homeScene = new Scene(container, 640, 480);
    }

    public static void createHistoryScene(Stage stage){
        TableView<SourceFile> importTable = createImportTable();

        Button toHomeSceneButton = new Button("To Sentence Builder");
        toHomeSceneButton.setOnAction(e -> stage.setScene(homeScene));

        VBox root = new VBox(20, importTable, toHomeSceneButton);
        root.setAlignment(Pos.CENTER);

        StackPane container = new StackPane(root);
        container.setAlignment(Pos.CENTER);

        historyScene = new Scene(container, 640, 480);
    }

    public static TableView<SourceFile> createImportTable(){
        TableView<SourceFile> importTable = new TableView<>();

        // Define Columns
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
        importTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        timestampCol.setSortType(TableColumn.SortType.DESCENDING);
        importTable.getSortOrder().add(timestampCol);
        importTable.sort();

        ObservableList<SourceFile> items = FXCollections.observableArrayList();
        importTable.setItems(items);

        importedFiles.addListener((MapChangeListener<String, SourceFile>) change -> {
            if (change.wasAdded()) {
                items.add(change.getValueAdded());
            }
            if (change.wasRemoved()) {
                items.remove(change.getValueRemoved());
            }
        });

        try{
            Map<String, SourceFile> dbFiles = db.getAllSourceFiles();
            importedFiles.putAll(dbFiles);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Updating table every 5 seconds
        Thread refresh = getThread();
        refresh.start();

        return importTable;
    }

    // Create a thread that updates the table every 5 seconds
    // If the new "updated" map contains a key that is not in "importedFiles", add it
    // Files removed from the database are also removed from the table
    private static Thread getThread() {
        Thread refresh = new Thread(() -> {
            while (true){
                try{
                    Thread.sleep(5000);
                    Map<String, SourceFile> updated = db.getAllSourceFiles();
                    Platform.runLater(() -> {
                        for (String key : updated.keySet()){
                            if(!importedFiles.containsKey(key)){
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
        return refresh;
    }
}