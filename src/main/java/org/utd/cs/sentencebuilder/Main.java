package org.utd.cs.sentencebuilder;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        DatabaseManager db = new DatabaseManager();

        try {
            // Build database if it doesn't exist
            db.buildDatabase();

            //hands the shared DatabaseManager instance to Javafx
            Javafx.setDatabaseManager(db);

            GeneratorController controller = new GeneratorController(db);
            Javafx.setGeneratorController(controller);

            Javafx.main(args); //blocks here until JavaFX application exits

            //db.clearAllData();
        } catch (SQLException e) {
            System.err.println("❌ Database operation failed: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // Cleanly shut down Hikari connection pool
            DatabaseManager.closeDataSource();
        }
    }
}
