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

public class Javafx extends Application {

    private static final int MAX_WORDS = 1;

    @Override
    public void start(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        // Upload File Row
        Button uploadButton = new Button("Upload a Text FIle");
        uploadButton.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(stage);
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

}