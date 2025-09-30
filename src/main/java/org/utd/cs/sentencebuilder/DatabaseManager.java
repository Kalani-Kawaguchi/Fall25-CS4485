package org.utd.cs.sentencebuilder;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;


public class DatabaseManager {
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        Properties props = new Properties();
        // Use a try-with-resources statement to automatically close the stream
        try (InputStream input = DatabaseManager.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Error: config.properties file not found in classpath.");
                throw new RuntimeException("Configuration file not found.");
            }
            // Load the properties from the file
            props.load(input);

            // Assign the loaded properties to the final static variables
            DB_URL = props.getProperty("db.url");
            DB_USER = props.getProperty("db.user");
            DB_PASSWORD = props.getProperty("db.password");

        } catch (Exception e) {
            e.printStackTrace();
            // Throw a runtime exception to fail fast if config is missing or invalid
            throw new RuntimeException("Failed to load database configuration.", e);
        }
    }


    public Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * Creates the database schema (tables and relationships).
     * This method is idempotent; it won't fail if the tables already exist.
     */
    public void buildDatabase() {
        String createWordsTable =
                "CREATE TABLE IF NOT EXISTS words (" +
                "word_id INT AUTO_INCREMENT PRIMARY KEY," +
                "word_value VARCHAR(255) UNIQUE NOT NULL," +
                "total_occurrences INT DEFAULT 1 NOT NULL," +
                "start_sentence_count INT DEFAULT 0 NOT NULL," +
                "end_sequence_count INT DEFAULT 0 NOT NULL" +
                ");";

        String createWordPairsTable =
                "CREATE TABLE IF NOT EXISTS word_pairs (" +
                "sequence_id INT AUTO_INCREMENT PRIMARY KEY," +
                "preceding_word_id INT NOT NULL," +
                "following_word_id INT NOT NULL," +
                "occurrence_count INT DEFAULT 1 NOT NULL," +
                "FOREIGN KEY (preceding_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "FOREIGN KEY (following_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "UNIQUE KEY unique_pair (preceding_word_id, following_word_id)" +
                ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            System.out.println("Building database schema...");
            stmt.execute(createWordsTable);
            System.out.println("Table 'words' created or already exists.");
            stmt.execute(createWordPairsTable);
            System.out.println("Table 'word_pairs' created or already exists.");
            System.out.println("Database build complete.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}