package org.utd.cs.sentencebuilder;

import java.sql.SQLException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        DatabaseManager db = new DatabaseManager();

        try {
            // Build database if it doesn't exist
            db.buildDatabase();

            Javafx.main(args);
        } catch (SQLException e) {
            System.err.println("❌ Database operation failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanly shut down Hikari connection pool
            DatabaseManager.closeDataSource();
        }
    }
}
