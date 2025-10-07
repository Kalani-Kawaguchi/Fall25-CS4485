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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final HikariDataSource dataSource;

    static {
        Properties dbProps = new Properties();
        Properties poolProps = new Properties();


        try (
                InputStream dbInput = DatabaseManager.class.getClassLoader().getResourceAsStream("config.properties");
                InputStream poolInput = DatabaseManager.class.getClassLoader().getResourceAsStream("pool.properties")
        ) {
            if (dbInput == null) {throw new RuntimeException("Database Config file not found.");}
            if (poolInput == null) {throw new RuntimeException("Pool Config file not found.");}

            dbProps.load(dbInput);
            poolProps.load(poolInput);

            logger.info("Attempting to connect to database: {}", dbProps.getProperty("db.url"));
            HikariConfig config = new HikariConfig(poolProps);

            config.setJdbcUrl(dbProps.getProperty("db.url"));
            config.setUsername(dbProps.getProperty("db.user"));
            config.setPassword(dbProps.getProperty("db.password"));
            dataSource = new HikariDataSource(config);
            logger.info("Database connection pool initialized successfully.");

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to initialize database connection pool.", e);
        }
    }

    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Closing database connection pool.");
            dataSource.close();
        }
    }

    private Connection getConnect() throws SQLException {
        return dataSource.getConnection();
    }



    /**
     * Creates the database schema.
     * This method is idempotent; it won't fail if the tables already exist.
     */
    public void buildDatabase() throws SQLException{
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

        try (Connection conn = getConnect(); Statement stmt = conn.createStatement()) {
            logger.info("Building database schema...");
            stmt.execute(createSourceFileTable);
            logger.info("Table 'source_file' created or already exists.");
            stmt.execute(createWordsTable);
            logger.info("Table 'words' created or already exists.");
            stmt.execute(createWordPairsTable);
            logger.info("Table 'word_pairs' created or already exists.");
            stmt.execute(createTrigramSequenceTable);
            logger.info("Table 'trigram_sequence' created or already exists.");
            logger.info("Database build complete.");
        } catch (SQLException e) {
            logger.error("Database build failed.", e);
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
        logger.info("Adding new source file record: {}", fileName);
        String sqlInsert = "INSERT INTO source_file(file_name, word_count) VALUES(?, ?)";
        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, fileName);
            pstmt.setInt(2, wordCount);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    logger.info("Successfully added source file '{}' with file_id {}.", fileName, newId);
                    return newId;
                } else {
                    throw new SQLException("Creating source file failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Inserts a new word or updates its counts if it already exists.
     *
     * @param word The Word object to add or update.
     * @return The word_id of the newly created word, or 0 if the word was updated.
     * @throws SQLException if a database access error occurs.
     */
    public int addWord(Word word) throws SQLException {
        logger.debug("Processing word: '{}'", word.getWordValue());
        String sql = "INSERT INTO words (word_value, total_occurrences, start_sentence_count, end_sequence_count) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "total_occurrences = total_occurrences + VALUES(total_occurrences), " +
                "start_sentence_count = start_sentence_count + VALUES(start_sentence_count), " +
                "end_sequence_count = end_sequence_count + VALUES(end_sequence_count)";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, word.getWordValue());
            pstmt.setInt(2, word.getTotalOccurrences());
            pstmt.setInt(3, word.getStartSentenceCount());
            pstmt.setInt(4, word.getEndSequenceCount());
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        logger.debug("Inserted new word '{}' with word_id {}.", word.getWordValue(), newId);
                        return newId;
                    } else {
                        logger.debug("Updated existing word '{}'.", word.getWordValue());
                        return 0;
                    }
                }
            } else {
                throw new SQLException("Creating/updating word failed, no rows affected.");
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
        if (wordCollection == null || wordCollection.isEmpty()) {
            logger.info("Word collection is empty. No action taken.");
            return;
        }
        logger.info("Executing batch insert/update for {} words.", wordCollection.size());
        String sql = "INSERT INTO words (word_value, total_occurrences, start_sentence_count, end_sequence_count) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "total_occurrences = total_occurrences + VALUES(total_occurrences), " +
                "start_sentence_count = start_sentence_count + VALUES(start_sentence_count), " +
                "end_sequence_count = end_sequence_count + VALUES(end_sequence_count)";

        try (Connection conn = getConnect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Word stats : wordCollection) {
                pstmt.setString(1, stats.getWordValue());
                pstmt.setInt(2, stats.getTotalOccurrences());
                pstmt.setInt(3, stats.getStartSentenceCount());
                pstmt.setInt(4, stats.getEndSequenceCount());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            logger.info("Batch execution for words complete.");
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
        logger.debug("Querying for word_id of '{}'.", word);
        String sql = "SELECT word_id FROM words WHERE word_value = ?";
        try (Connection conn = getConnect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, word);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int wordId = rs.getInt("word_id");
                    logger.debug("Found word_id {} for word '{}'.", wordId, word);
                    return wordId;
                }
            }
        }
        logger.debug("Word '{}' not found in database.", word);
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
        logger.debug("Querying for word_ids of {} words.", words.size());

        String placeholders = String.join(",", java.util.Collections.nCopies(words.size(), "?"));
        String sql = "SELECT word_value, word_id FROM words WHERE word_value IN (" + placeholders + ")";

        try (Connection conn = getConnect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        logger.debug("Retrieved {} word_ids.", wordIdMap.size());
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
        logger.debug("Querying for full word object for '{}'.", wordValue);
        String sql = "SELECT * FROM words WHERE word_value = ?";
        try (Connection conn = getConnect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, wordValue);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Word word = new Word(rs.getString("word_value"));
                    word.setWordId(rs.getInt("word_id"));
                    word.setTotalOccurrences(rs.getInt("total_occurrences"));
                    word.setStartSentenceCount(rs.getInt("start_sentence_count"));
                    word.setEndSequenceCount(rs.getInt("end_sequence_count"));
                    logger.debug("Found word object for '{}'.", wordValue);
                    return word;
                }
            }
        }
        logger.debug("Word object for '{}' not found.", wordValue);
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
        logger.debug("Querying for full word object for word_id {}.", wordId);
        String sql = "SELECT * FROM words WHERE word_id = ?";
        try (Connection conn = getConnect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, wordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Word word = new Word(rs.getString("word_value"));
                    word.setWordId(rs.getInt("word_id"));
                    word.setTotalOccurrences(rs.getInt("total_occurrences"));
                    word.setStartSentenceCount(rs.getInt("start_sentence_count"));
                    word.setEndSequenceCount(rs.getInt("end_sequence_count"));
                    logger.debug("Found word object for word_id {}.", wordId);
                    return word;
                }
            }
        }
        logger.debug("Word object for word_id {} not found.", wordId);
        return null; // Not found
    }

    /**
     * Inserts a new word pair or updates the occurrence count if it already exists.
     *
     * @param pair The WordPair object containing the preceding and following word IDs.
     * @return The sequence_id of the newly created word pair, or 0 if the pair was updated.
     * @throws SQLException if a database access error occurs.
     */
    public int addWordPair(WordPair pair) throws SQLException {
        logger.debug("Processing word pair: preceding_id={}, following_id={}", pair.getPrecedingWordId(), pair.getFollowingWordId());
        String sql = "INSERT INTO word_pairs (preceding_word_id, following_word_id, occurrence_count) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE occurrence_count = occurrence_count + VALUES(occurrence_count)";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, pair.getPrecedingWordId());
            pstmt.setInt(2, pair.getFollowingWordId());
            pstmt.setInt(3, pair.getOccurrenceCount() > 0 ? pair.getOccurrenceCount() : 1);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        logger.debug("Inserted new word pair with sequence_id {}.", newId);
                        return newId;
                    } else {
                        return 0;
                    }
                }
            } else {
                throw new SQLException("Creating/updating word pair failed, no rows affected.");
            }
        }
    }

    /**
     * Inserts or updates a collection of word pairs in a single batch operation.
     *
     * @param wordPairs A collection of WordPair objects to be added or updated.
     * @throws SQLException if a database access error occurs.
     */
    public void bulkAddWordPairs(Collection<WordPair> wordPairs) throws SQLException {
        String sql = "INSERT INTO word_pairs (preceding_word_id, following_word_id, occurrence_count) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE occurrence_count = occurrence_count + VALUES(occurrence_count)";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (wordPairs == null || wordPairs.isEmpty()) {
                logger.info("Word pairs collection is empty. No action taken.");
                return;
            }

            for (WordPair pair : wordPairs) {
                pstmt.setInt(1, pair.getPrecedingWordId());
                pstmt.setInt(2, pair.getFollowingWordId());
                pstmt.setInt(3, pair.getOccurrenceCount());
                pstmt.addBatch();
            }
            logger.info("Executing batch insert/update for {} word pairs.", wordPairs.size());
            pstmt.executeBatch();
            logger.info("Batch execution for word pairs complete.");
        }
    }

    /**
     * Deletes all data from all tables in the database.
     * This is a destructive operation.
     * Resets auto-increment counters.
     *
     * @throws SQLException if a database access error occurs.
     */

    public void clearAllData() throws SQLException {
        logger.warn("--- DELETING ALL DATA FROM DATABASE ---");
        String[] tables = {"trigram_sequence", "word_pairs", "words", "source_file"};

        try (Connection conn = getConnect(); Statement stmt = conn.createStatement()) {
            try {
                logger.info("Disabling foreign key checks.");
                stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

                for (String table : tables) {
                    String sql = "TRUNCATE TABLE " + table;
                    logger.info("Executing: {}", sql);
                    stmt.addBatch(sql);
                }
                stmt.executeBatch();

                logger.warn("--- DATABASE CLEARED SUCCESSFULLY ---");
            } finally {
                logger.info("Re-enabling foreign key checks.");
                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

}
