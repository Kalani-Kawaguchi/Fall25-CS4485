/**
 * DatabaseManager.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Vincent Phan
 * Date: September 29, 2025
 *
 * Description:
 * This class serves as the data access layer for the Sentence Builder application.
 * It encapsulates all database interactions. It provides methods for both
 * individual and bulk data operations.
 */

package org.utd.cs.sentencebuilder;

import java.io.InputStream;
import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
     * Creates the database schema.
     * This method is idempotent; it won't fail if the tables already exist.
     */
    public void buildDatabase() {
        String createSourceFileTable =
                "CREATE TABLE IF NOT EXISTS source_file (" +
                "file_id INT AUTO_INCREMENT PRIMARY KEY," +
                "file_name VARCHAR(255) UNIQUE NOT NULL," +
                "word_count INT NOT NULL," +
                "import_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL" +
                ");";

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

        String createTrigramSequenceTable =
                "CREATE TABLE IF NOT EXISTS trigram_sequence (" +
                "sequence_id INT AUTO_INCREMENT PRIMARY KEY," +
                "first_word_id INT NOT NULL," +
                "second_word_id INT NOT NULL," +
                "third_word_id INT NOT NULL," +
                "follows_count INT DEFAULT 1 NOT NULL," +
                "FOREIGN KEY (first_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "FOREIGN KEY (second_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "FOREIGN KEY (third_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "UNIQUE KEY unique_trigram (first_word_id, second_word_id, third_word_id)" +
                ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            System.out.println("Building database schema...");
            stmt.execute(createSourceFileTable);
            System.out.println("Table 'source_file' created or already exists.");
            stmt.execute(createWordsTable);
            System.out.println("Table 'words' created or already exists.");
            stmt.execute(createWordPairsTable);
            System.out.println("Table 'word_pairs' created or already exists.");
            stmt.execute(createTrigramSequenceTable);
            System.out.println("Table 'trigram_sequence' created or already exists.");
            System.out.println("Database build complete.");
        } catch (SQLException e) {
            System.err.println("Database build failed.");
            e.printStackTrace();
        }
    }
    /**
     * Adds a new source file record to the database.
     *
     * @param fileName   The name of the file being imported.
     * @param wordCount  The total number of words in the file.
     * @return The auto-generated file_id of the newly inserted record.
     * @throws SQLException if a database access error occurs.
     */
    public int addSourceFile(String fileName, int wordCount) throws SQLException {
        String sqlInsert = "INSERT INTO source_file(file_name, word_count) VALUES(?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, fileName);
            pstmt.setInt(2, wordCount);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating source file failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Inserts or updates a collection of words in a single batch operation.
     *
     * @param wordCollection A collection of Word objects with aggregated data.
     * @throws SQLException if a database access error occurs.
     */
    public void addWordsInBatch(Collection<Word> wordCollection) throws SQLException {
        String sql = "INSERT INTO words (word_value, total_occurrences, start_sentence_count, end_sequence_count) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "total_occurrences = total_occurrences + VALUES(total_occurrences), " +
                "start_sentence_count = start_sentence_count + VALUES(start_sentence_count), " +
                "end_sequence_count = end_sequence_count + VALUES(end_sequence_count)";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Word stats : wordCollection) {
                pstmt.setString(1, stats.getWordValue());
                pstmt.setInt(2, stats.getTotalOccurrences());
                pstmt.setInt(3, stats.getStartSentenceCount());
                pstmt.setInt(4, stats.getEndSequenceCount());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    /**
     * Retrieves the ID of a word from the database.
     *
     * @param word The word to look up.
     * @return The word_id, or -1 if the word is not found.
     * @throws SQLException if a database access error occurs.
     */
    public int getWordId(String word) throws SQLException {
        String sql = "SELECT word_id FROM words WHERE word_value = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, word);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("word_id");
                }
            }
        }
        return -1; // Not found
    }

    /**
     * Retrieves a map of word strings to their corresponding word_ids in a single query.
     *
     * @param words A collection of Word objects to look up.
     * @return A Map where keys are the word strings and values are their database IDs.
     * @throws SQLException if a database access error occurs.
     */
    public Map<String, Integer> getWordIds(Collection<Word> words) throws SQLException {
        Map<String, Integer> wordIdMap = new HashMap<>();
        if (words == null || words.isEmpty()) {
            return wordIdMap;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(words.size(), "?"));
        String sql = "SELECT word_value, word_id FROM words WHERE word_value IN (" + placeholders + ")";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int i = 1;
            for (Word word : words) {
                pstmt.setString(i++, word.getWordValue());
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    wordIdMap.put(rs.getString("word_value"), rs.getInt("word_id"));
                }
            }
        }
        return wordIdMap;
    }


    /**
     * Retrieves a complete Word object from the database by its value.
     *
     * @param wordValue The word string to look up.
     * @return A Word object with all its data, or null if not found.
     * @throws SQLException if a database access error occurs.
     */
    public Word getWord(String wordValue) throws SQLException {
        String sql = "SELECT * FROM words WHERE word_value = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, wordValue);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Word word = new Word(rs.getString("word_value"));
                    word.setWordId(rs.getInt("word_id"));
                    word.setTotalOccurrences(rs.getInt("total_occurrences"));
                    word.setStartSentenceCount(rs.getInt("start_sentence_count"));
                    word.setEndSequenceCount(rs.getInt("end_sequence_count"));
                    return word;
                }
            }
        }
        return null; // Not found
    }

    /**
     * Retrieves a complete Word object from the database by its ID.
     *
     * @param wordId The ID of the word to look up.
     * @return A Word object with all its data, or null if not found.
     * @throws SQLException if a database access error occurs.
     */
    public Word getWord(int wordId) throws SQLException {
        String sql = "SELECT * FROM words WHERE word_id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, wordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Word word = new Word(rs.getString("word_value"));
                    word.setWordId(rs.getInt("word_id"));
                    word.setTotalOccurrences(rs.getInt("total_occurrences"));
                    word.setStartSentenceCount(rs.getInt("start_sentence_count"));
                    word.setEndSequenceCount(rs.getInt("end_sequence_count"));
                    return word;
                }
            }
        }
        return null; // Not found
    }

}
