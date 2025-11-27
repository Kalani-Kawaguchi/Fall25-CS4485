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
import java.util.*;
import java.util.function.Consumer;


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
            throw new RuntimeException("Failed to initialize database connection pool.", e);
        }
    }

    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing database connection pool.");
            dataSource.close();
        }
    }

    public Connection getConnect() throws SQLException {
        return dataSource.getConnection();
    }

    // Public-facing connection getter for external classes (e.g., sentence generators)
    public Connection getConnection() throws SQLException {
        return getConnect();
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
                "bi_occurrence_count INT DEFAULT 1 NOT NULL," +
                "bi_end_frequency INT DEFAULT 0 NOT NULL," +
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
                "tri_occurrence_count INT DEFAULT 1 NOT NULL," +
                "tri_end_frequency INT DEFAULT 0 NOT NULL," +
                "FOREIGN KEY (first_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "FOREIGN KEY (second_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "FOREIGN KEY (third_word_id) REFERENCES words(word_id) ON DELETE CASCADE," +
                "UNIQUE KEY unique_trigram (first_word_id, second_word_id, third_word_id)" +
                ");";

        String createSentenceTable =
                "CREATE TABLE IF NOT EXISTS sentences (" +
                "sentence_id INT AUTO_INCREMENT PRIMARY KEY," +
                "text TEXT NOT NULL," + // consider hashing to compare on hash rather than word
                "token_count INT NOT NULL," +
                "sentence_occurrences INT NOT NULL DEFAULT 1," +
                "sentence_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(text, 256))) STORED," +
                "UNIQUE KEY uk_sentence_hash (sentence_hash)" + //for uniqueness and searching
                ")";

        String createSentenceHistogram = """
                CREATE TABLE IF NOT EXISTS sentence_histogram(
                sentence_length INT NOT NULL PRIMARY KEY,
                frequency INT NOT NULL DEFAULT 0,
                tail_count BIGINT DEFAULT 0,
                hazard DOUBLE DEFAULT 0.0
                )
                """;

        // Do not try to normalize or use references for this table.
        String createSentenceFeatureTable =
                "CREATE TABLE IF NOT EXISTS sentence_features (" +
                "sentence_id INT NOT NULL," +
                "token_index INT NOT NULL," +
                "word VARCHAR(255) NOT NULL," +
                "context_type ENUM('unigram', 'bigram', 'trigram', 'synthetic') NOT NULL," +
                "context_ngram VARCHAR(512) NOT NULL," +
                "sentence_len INT NOT NULL," +
                "p_eos_context DOUBLE," +
                "p_eos_word DOUBLE," +
                "p_eos_length DOUBLE," +
                "x1 DOUBLE," +           // logit(P(EOS|context))
                "x2 DOUBLE," +           // logit(P(EOS|word))
                "x3 DOUBLE," +           // logit(P(EOS|length))
                "label TINYINT NOT NULL," +  // 1 if EOS follows, else 0
                "PRIMARY KEY (sentence_id, token_index)" +
                ")";


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
            stmt.execute(createSentenceTable);
            logger.info("Table 'sentences' created or already exists.");
            stmt.execute(createSentenceHistogram);
            logger.info("Table 'sentence_histogram' created or already exists.");
            stmt.execute(createSentenceFeatureTable);
            logger.info("Table 'sentence_features' created or already exists.");
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
     * Retrieves all source files from the database and returns them as a map.
     *
     * @return A Map where the key is the file_name (String) and the value is the complete SourceFile record.
     * @throws SQLException if a database access error occurs.
     */
    public Map<String, SourceFile> getAllSourceFiles() throws SQLException {
        logger.info("Retrieving all source files from the database.");
        Map<String, SourceFile> fileMap = new HashMap<>();
        String sql = "SELECT file_id, file_name, word_count, import_timestamp FROM source_file";

        // Assumes you have a getConnect() method like in your example
        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                SourceFile file = new SourceFile(
                        rs.getInt("file_id"),
                        rs.getString("file_name"),
                        rs.getInt("word_count"),
                        rs.getTimestamp("import_timestamp")
                );

                fileMap.put(file.fileName(), file);
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve all source files.", e);
            throw e;
        }
        logger.info("Successfully retrieved {} source files.", fileMap.size());
        return fileMap;
    }

    /**
     * Inserts a collection of sentences in a single batch operation.
     *
     * @param sentenceCollection A collection of Sentence records.
     * @throws SQLException if a database access error occurs.
     */
    public void addSentencesInBatch(Collection<Sentence> sentenceCollection) throws SQLException {
        if (sentenceCollection == null || sentenceCollection.isEmpty()) {
            logger.info("Sentence collection is empty. No action taken.");
            return;
        }
        logger.info("Executing batch insert for {} sentences.", sentenceCollection.size());
        String sql = "INSERT INTO sentences (text, token_count, sentence_occurrences) VALUES (?, ?, ?)" +
                "ON DUPLICATE KEY UPDATE " +
                "sentence_occurrences = sentence_occurrences + VALUES(sentence_occurrences)";
        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Sentence sentence : sentenceCollection) {
                pstmt.setString(1, sentence.getText());
                pstmt.setInt(2, sentence.getTokenCount());
                pstmt.setInt(3, sentence.getSentenceOccurrences());
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            logger.info("Batch execution for sentences complete.");
        }
    }

    /**
     * Streams all sentences from the database and processes them one by one
     * using the provided Consumer.
     *
     * This is the memory-efficient approach, ideal for very large datasets.
     * It uses the streaming hints from your original method.
     *
     * @param sentenceConsumer A function that accepts and processes a single Sentence.
     * @throws SQLException if a database access error occurs.
     */
    public void processSentences(Consumer<Sentence> sentenceConsumer) throws SQLException {
        String sql = "SELECT sentence_id, text, token_count, sentence_occurrences FROM sentences";

        // Ensures the Connection, PreparedStatement, and ResultSet are all
        // closed automatically, even if an error occurs during processing.
        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(
                     sql,
                     ResultSet.TYPE_FORWARD_ONLY,
                     ResultSet.CONCUR_READ_ONLY
             )) {

            pstmt.setFetchSize(Integer.MIN_VALUE);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // Build the sentence object
                    Sentence sentence = new Sentence(
                            rs.getInt("sentence_id"),
                            rs.getString("text"),
                            rs.getInt("token_count"),
                            rs.getInt("sentence_occurrences")
                    );
                    // Pass the built object to the consumer
                    sentenceConsumer.accept(sentence);
                }
            }
        }
    }

    /**
     * Takes a map of {sentence_length -> frequency} and inserts or updates
     * the counts in the database in a single batch.
     *
     * @param lengthHistogram A map where the key is the sentence length
     * and the value is the count to add for that length.
     * @throws SQLException if a database access error occurs.
     */
    public void addSentenceLengthsInBatch(Map<Integer, Integer> lengthHistogram) throws SQLException {
        if (lengthHistogram == null || lengthHistogram.isEmpty()) {
            logger.info("Sentence length histogram map is empty. No action taken.");
            return;
        }
        logger.info("Executing batch insert/update for " + lengthHistogram.size() + " histogram entries.");

        String sql = "INSERT INTO sentence_histogram (sentence_length, frequency) " +
                "VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "frequency = frequency + VALUES(frequency)";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Map.Entry<Integer, Integer> entry : lengthHistogram.entrySet()) {
                Integer sentenceLength = entry.getKey();
                Integer countToAdd = entry.getValue();

                pstmt.setInt(1, sentenceLength);
                pstmt.setInt(2, countToAdd);
                pstmt.addBatch();
            }

            // Execute all statements in the batch
            pstmt.executeBatch();
            logger.info("Batch execution for histogram update complete.");
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
     * Retrieves a map of ALL word strings to their corresponding word_ids.
     *
     * @return A Map where keys are all word strings in the database and
     * values are their corresponding database IDs.
     * @throws SQLException if a database access error occurs.
     */
    public Map<String, Integer> getWordIds() throws SQLException {
        Map<String, Integer> wordIdMap = new HashMap<>();
        logger.debug("Querying for all word_ids in the database.");

        // SQL query to select all words and their IDs
        String sql = "SELECT word_value, word_id FROM words";

        // Use try-with-resources to ensure all resources are closed
        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) { // No parameters to set

            while (rs.next()) {
                wordIdMap.put(rs.getString("word_value"), rs.getInt("word_id"));
            }
        }

        logger.debug("Retrieved {} total word_ids.", wordIdMap.size());
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
                    word.setEndSequenceCount(rs.getInt("bi_end_frequency"));
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
     * Retrieves all words from the database and returns them as a map.
     *
     * @return A Map where the key is the word_id (Integer) and the value is the complete Word object.
     * @throws SQLException if a database access error occurs.
     */
    public Map<Integer, Word> getAllWords() throws SQLException {
        logger.info("Retrieving all words from the database to build dictionary.");
        Map<Integer, Word> wordMap = new HashMap<>();
        String sql = "SELECT word_id, word_value, total_occurrences, start_sentence_count, end_sequence_count FROM words";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Word word = new Word(rs.getString("word_value"));
                word.setWordId(rs.getInt("word_id"));
                word.setTotalOccurrences(rs.getInt("total_occurrences"));
                word.setStartSentenceCount(rs.getInt("start_sentence_count"));
                word.setEndSequenceCount(rs.getInt("end_sequence_count"));
                wordMap.put(word.getWordId(), word);
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve all words.", e);
            throw e;
        }

        logger.info("Successfully retrieved {} words.", wordMap.size());
        return wordMap;
    }

    public Map<Integer, String> getALlWordsAsString() throws SQLException {
        logger.info("Retrieving all words as strings from the database to build dictionary.");
        Map<Integer, String> wordMap = new HashMap<>();
        String sql = """
                SELECT word_id, word_value FROM words
                """;

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                wordMap.put(rs.getInt("word_id"), rs.getString("word_value"));
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve all words.", e);
            throw e;
        }
        logger.info("Successfully retrieved {} words.", wordMap.size());
        return wordMap;
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
        String sql = "INSERT INTO word_pairs (preceding_word_id, following_word_id, bi_occurrence_count, bi_end_frequency) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE bi_occurrence_count = bi_occurrence_count + VALUES(bi_occurrence_count), bi_end_frequency + VALUES(bi_end_frequency)";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, pair.getPrecedingWordId());
            pstmt.setInt(2, pair.getFollowingWordId());
            pstmt.setInt(3, pair.getOccurrenceCount() > 0 ? pair.getOccurrenceCount() : 1);
            pstmt.setInt(4, pair.getEndFrequency());
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
    public void addWordPairsInBatch(Collection<WordPair> wordPairs) throws SQLException {
        String sql = "INSERT INTO word_pairs (preceding_word_id, following_word_id, bi_occurrence_count, bi_end_frequency) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "bi_occurrence_count = bi_occurrence_count + VALUES(bi_occurrence_count), " +
                "bi_end_frequency = bi_end_frequency + VALUES(bi_end_frequency)";

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
                pstmt.setInt(4, pair.getEndFrequency());
                pstmt.addBatch();
            }
            logger.info("Executing batch insert/update for {} word pairs.", wordPairs.size());
            pstmt.executeBatch();
            logger.info("Batch execution for word pairs complete.");
        }
    }

    /**
     * Retrieves all word pairs from the database as a nested Map
     * for efficient lookups: Map<PrecedingID, Map<FollowingID, WordPair>>
     *
     * This replaces the previous implementation that returned a Collection.
     *
     * @return A nested Map structure of all word pairs.
     * @throws SQLException if a database access error occurs.
     */
    public Map<Integer, Map<Integer, WordPair>> getAllWordPairs() throws SQLException {
        logger.info("Retrieving all word pairs as a nested map.");
        // The outer map. Key: precedingWordId
        Map<Integer, Map<Integer, WordPair>> nestedPairMap = new HashMap<>();
        String sql = "SELECT sequence_id, preceding_word_id, following_word_id, bi_occurrence_count, bi_end_frequency FROM word_pairs";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                WordPair pair = new WordPair();
                int precedingId = rs.getInt("preceding_word_id");
                int followingId = rs.getInt("following_word_id");

                pair.setSequenceId(rs.getInt("sequence_id"));
                pair.setPrecedingWordId(precedingId);
                pair.setFollowingWordId(followingId);
                pair.setOccurrenceCount(rs.getInt("bi_occurrence_count"));
                pair.setEndFrequency(rs.getInt("bi_end_frequency"));

                // Get the inner map for this precedingId.
                // If it doesn't exist, create it and put it in the outer map.
                Map<Integer, WordPair> innerMap = nestedPairMap.computeIfAbsent(
                        precedingId,
                        k -> new HashMap<>()
                );

                // Add the pair to the inner map.
                innerMap.put(followingId, pair);
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve all word pairs for nested map.", e);
            throw e;
        }

        logger.info("Successfully retrieved and mapped {} preceding word IDs.", nestedPairMap.size());
        return nestedPairMap;
    }

    public Map<Integer, List<int[]>> getBigramMap() throws SQLException {
        logger.info("Retrieving bigram mapping.");
        String sql = """
                SELECT preceding_word_id, following_word_id, bi_occurrence_count
                FROM word_pairs
                """;

        Map<Integer, List<int[]>> bigramMap = new HashMap<>();

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int prev  = rs.getInt("preceding_word_id");
                int next  = rs.getInt("following_word_id");
                int count = rs.getInt("bi_occurrence_count");

                bigramMap
                        .computeIfAbsent(prev, k -> new ArrayList<>())
                        .add(new int[]{ next, count });
            }
        } catch (SQLException e) {
            logger.error("Failed to create bigram map.", e);
            throw e;
        }

        for (List<int[]> list : bigramMap.values()) {
            list.sort((x, y) -> Integer.compare(y[1], x[1]));
        }
        logger.info("Successfully retrieved {} bigrams.", bigramMap.size());
        return bigramMap;
    }

    /**
     * Inserts or updates a collection of word triplets in a single batch operation.
     *
     * @param wordTriplets A collection of WordTriplet objects to be added or updated.
     * @throws SQLException if a database access error occurs.
     */
    public void addWordTripletsInBatch(Collection<WordTriplet> wordTriplets) throws SQLException {
        String sql = "INSERT INTO trigram_sequence (first_word_id, second_word_id, third_word_id, tri_occurrence_count, tri_end_frequency) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "tri_occurrence_count = tri_occurrence_count + VALUES(tri_occurrence_count), " +
                "tri_end_frequency = tri_end_frequency + VALUES(tri_end_frequency)";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (wordTriplets == null || wordTriplets.isEmpty()) {
                logger.info("Word triplets collection is empty. No action taken.");
                return;
            }

            for (WordTriplet triplet : wordTriplets) {
                pstmt.setInt(1, triplet.getFirstWordId());
                pstmt.setInt(2, triplet.getSecondWordId());
                pstmt.setInt(3, triplet.getThirdWordId());
                pstmt.setInt(4, triplet.getOccurrenceCount());
                pstmt.setInt(5, triplet.getEndFrequency());
                pstmt.addBatch();
            }

            logger.info("Executing batch insert/update for {} word triplets.", wordTriplets.size());
            pstmt.executeBatch();
            logger.info("Batch execution for word triplets complete.");
        }
    }

    /**
     * Retrieves all word triplets from the database as a doubly-nested Map
     * for efficient lookups: Map<FirstID, Map<SecondID, Map<ThirdID, WordTriplet>>>
     *
     * @return A doubly-nested Map structure of all word triplets.
     * @throws SQLException if a database access error occurs.
     */
    public Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> getAllWordTriplets() throws SQLException {
        logger.info("Retrieving all word triplets as a nested map.");
        // The outer map. Key: firstWordId
        Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> nestedTripletMap = new HashMap<>();

        // Assumed table/column names based on WordTriplet POJO
        String sql = """
                SELECT sequence_id, first_word_id, second_word_id, third_word_id,
                tri_occurrence_count, tri_end_frequency FROM trigram_sequence
                """;
        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                WordTriplet triplet = new WordTriplet();
                int firstId = rs.getInt("first_word_id");
                int secondId = rs.getInt("second_word_id");
                int thirdId = rs.getInt("third_word_id");

                triplet.setSequenceId(rs.getInt("sequence_id"));
                triplet.setFirstWordId(firstId);
                triplet.setSecondWordId(secondId);
                triplet.setThirdWordId(thirdId);
                triplet.setOccurrenceCount(rs.getInt("tri_occurrence_count"));
                triplet.setEndFrequency(rs.getInt("tri_end_frequency"));

                // Get the middle map for this firstId.
                Map<Integer, Map<Integer, WordTriplet>> middleMap = nestedTripletMap.computeIfAbsent(
                        firstId,
                        k -> new HashMap<>()
                );

                // Get the inner map for this secondId.
                Map<Integer, WordTriplet> innerMap = middleMap.computeIfAbsent(
                        secondId,
                        k -> new HashMap<>()
                );

                // Add the triplet to the inner map.
                innerMap.put(thirdId, triplet);
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve all word triplets for nested map.", e);
            throw e;
        }

        logger.info("Successfully retrieved and mapped {} first word IDs.", nestedTripletMap.size());
        return nestedTripletMap;
    }

    public Map<Long, List<int[]>> getTrigramMap() throws SQLException {
        logger.info("Retrieving trigram mapping.");
        String sql = """
                SELECT first_word_id, second_word_id, third_word_id, tri_occurrence_count
                FROM trigram_sequence
                """;

        Map<Long, List<int[]>> trigramMap = new HashMap<>();

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int w1    = rs.getInt("first_word_id");
                int w2    = rs.getInt("second_word_id");
                int w3    = rs.getInt("third_word_id");
                int count = rs.getInt("tri_occurrence_count");

                long key = (((long) w1) << 32) | (w2 & 0xffffffffL);

                trigramMap
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new int[]{ w3, count });
            }
        } catch (SQLException e) {
            logger.error("Failed to create bigram map.", e);
            throw e;
        }

        for (List<int[]> list : trigramMap.values()) {
            list.sort((x, y) -> Integer.compare(y[1], x[1]));
        }
        logger.info("Successfully retrieved {} trigram.", trigramMap.size());
        return trigramMap;
    }

    /**
     * Inserts or updates a batch of sentence feature records in the database.
     * Each feature corresponds to a token-level EOS prediction context.
     *
     * @param features The collection of SentenceFeature objects to insert.
     * @throws SQLException if a database access error occurs.
     */
    public void addSentenceFeaturesInBatch(Collection<SentenceFeature> features) throws SQLException {
        logger.info("Inserting {} sentence features in batch.", features.size());
        //I forgot java had text blocks...
        String sql = """
        INSERT INTO sentence_features (
            sentence_id, token_index, word, context_type, context_ngram,
            sentence_len, p_eos_context, p_eos_word, p_eos_length,
            x1, x2, x3, label
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            p_eos_context = VALUES(p_eos_context),
            p_eos_word = VALUES(p_eos_word),
            p_eos_length = VALUES(p_eos_length),
            x1 = VALUES(x1), x2 = VALUES(x2), x3 = VALUES(x3),
            label = VALUES(label)
        """;

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (SentenceFeature f : features) {
                pstmt.setInt(1, f.getSentenceId());
                pstmt.setInt(2, f.getTokenIndex());
                pstmt.setString(3, f.getWord());
                pstmt.setString(4, f.getContextType());
                pstmt.setString(5, f.getContextNgram());
                pstmt.setInt(6, f.getSentenceLen());
                pstmt.setDouble(7, f.getpEosContext());
                pstmt.setDouble(8, f.getpEosWord());
                pstmt.setDouble(9, f.getpEosLength());
                pstmt.setDouble(10, f.getX1());
                pstmt.setDouble(11, f.getX2());
                pstmt.setDouble(12, f.getX3());
                pstmt.setInt(13, f.getLabel());
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            logger.info("Batch sentence feature insertion complete.");
        } catch (SQLException e) {
            logger.error("Failed to insert sentence features.", e);
            throw e;
        }
    }

    /**
     * Retrieves all sentence feature records from the database.
     *
     * @return A List of all SentenceFeature objects.
     * @throws SQLException if a database access error occurs.
     */
    public List<SentenceFeature> getAllSentenceFeatures() throws SQLException {
        logger.info("Retrieving all sentence features from database.");
        String sql = "SELECT * FROM sentence_features";
        List<SentenceFeature> features = new ArrayList<>();

        try (Connection conn = getConnect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                SentenceFeature f = new SentenceFeature();

                f.setSentenceId(rs.getInt("sentence_id"));
                f.setTokenIndex(rs.getInt("token_index"));
                f.setWord(rs.getString("word"));
                f.setContextType(rs.getString("context_type"));
                f.setContextNgram(rs.getString("context_ngram"));
                f.setSentenceLen(rs.getInt("sentence_len"));
                f.setpEosContext(rs.getDouble("p_eos_context"));
                f.setpEosWord(rs.getDouble("p_eos_word"));
                f.setpEosLength(rs.getDouble("p_eos_length"));
                f.setX1(rs.getDouble("x1"));
                f.setX2(rs.getDouble("x2"));
                f.setX3(rs.getDouble("x3"));
                f.setLabel(rs.getInt("label"));

                features.add(f);
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve sentence features.", e);
            throw e; // Re-throw the exception
        }

        logger.info("Successfully retrieved {} sentence features.", features.size());
        return features;
    }

    public void recomputeLengthHazards() throws SQLException {
        logger.info("Recomputing hazards.");

        String sql = "WITH hist AS (SELECT sentence_length, frequency, " +
                "SUM(frequency) OVER (ORDER BY sentence_length DESC) AS tail_count " +
                "FROM sentence_histogram) " +
                "UPDATE sentence_histogram h " +
                "JOIN hist ON h.sentence_length = hist.sentence_length " +
                "SET h.tail_count = hist.tail_count, " +
                "h.hazard = CASE WHEN hist.tail_count > 0 THEN hist.frequency / hist.tail_count ELSE 0 END;";
        try (Connection conn = getConnect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    /**
     * Retrieves the complete sentence length histogram as a Map
     * Map<Length, P(EOS|Length)>
     *
     * @return A Map where Key = sentence_length and Value = hazard.
     * @throws SQLException if a database access error occurs.
     */
    public Map<Integer, Double> getLengthProbabilityMap() throws SQLException {
        logger.info("Retrieving sentence length probability histogram.");
        Map<Integer, Double> lengthMap = new HashMap<>();
        String sql = "SELECT sentence_length, hazard FROM sentence_histogram";

        try (Connection conn = getConnect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                lengthMap.put(
                        rs.getInt("sentence_length"),
                        rs.getDouble("hazard")
                );
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve sentence length histogram.", e);
            throw e;
        }

        logger.info("Successfully retrieved {} length/hazard pairs.", lengthMap.size());
        return lengthMap;
    }

    /**
     * Prunes statistical outliers from the dataset based on sentence length.
     * Calculates the 99th percentile length and deletes anything longer from
     * sentences, histogram, and features.
     *
     *
     * @param percentile The target percentile to keep (e.g., 0.99).
     * @return The number of sentences removed.
     * @throws SQLException
     */
    public int pruneOutliers(double percentile) throws SQLException {
        logger.info("Pruning dataset to keep {}% (removing outliers)...", percentile * 100);

        List<Integer> lengths = new ArrayList<>();
        int cutoffLength = 0;

        // 1. Analyze Distribution
        try (Connection conn = getConnect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT token_count FROM sentences")) {

            while (rs.next()) {
                lengths.add(rs.getInt("token_count"));
            }
        }

        if (lengths.isEmpty()) {
            logger.warn("Database is empty. No pruning performed.");
            return 0;
        }

        // 2. Calculate Cutoff
        Collections.sort(lengths);
        int index = (int) (lengths.size() * percentile);
        index = Math.max(0, Math.min(index, lengths.size() - 1));
        cutoffLength = lengths.get(index);

        // Safety floor to prevent over-pruning on small datasets
        if (cutoffLength < 5) cutoffLength = 5;

        logger.info("Distribution Analysis: Max Length={}, {} Percentile Cutoff={}",
                lengths.get(lengths.size() - 1), percentile, cutoffLength);

        // 3. Execute Deletion (Transactional)
        // We delete from sentences AND histogram to ensure stats are clean.
        // We also clean features in case this is run late, but we respect synthetic IDs (negative).
        String delFeatures = "DELETE FROM sentence_features WHERE sentence_len > ? AND sentence_id >= 0";
        String delSentences = "DELETE FROM sentences WHERE token_count > ?";
        String delHistogram = "DELETE FROM sentence_histogram WHERE sentence_length > ?";

        try (Connection conn = getConnect()) {
            conn.setAutoCommit(false); // Start Transaction

            try (PreparedStatement psFeat = conn.prepareStatement(delFeatures);
                 PreparedStatement psSent = conn.prepareStatement(delSentences);
                 PreparedStatement psHist = conn.prepareStatement(delHistogram)) {

                // Delete children first (Features)
                psFeat.setInt(1, cutoffLength);
                int featCount = psFeat.executeUpdate();

                // Delete parents second (Sentences)
                psSent.setInt(1, cutoffLength);
                int sentCount = psSent.executeUpdate();

                // Delete histogram entries for removed lengths
                // This is crucial: recomputeLengthHazards relies on this table being accurate.
                psHist.setInt(1, cutoffLength);
                int histCount = psHist.executeUpdate();

                conn.commit();

                logger.info("PRUNE COMPLETE: Removed {} sentences, {} features, and {} histogram entries > length {}",
                        sentCount, featCount, histCount, cutoffLength);

                return sentCount;

            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                logger.error("Pruning failed. Rolled back changes.", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
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
        String[] tables = {"sentence_features","sentences", "trigram_sequence", "word_pairs", "words", "source_file"};

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
