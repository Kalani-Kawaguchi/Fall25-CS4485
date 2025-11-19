/**
 *  Author: Kevin Tran
 *  CS 4485 – Fall 2025
 *  November 19 2025
 *
 *  Description:
 *  Loads and caches all generator-related data from the database including:
 *   - Word → ID mappings
 *   - ID → Word mappings
 *   - Bigram followers (ID → [(nextId, count)])
 *   - Sentence-start candidate words
 *
 *  This class ensures the data is loaded only once and shared across
 *  all sentence generation strategies (greedy, weighted, beam search, etc.)
 *  for consistency and performance.
 */

package org.utd.cs.sentencebuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class GeneratorDataController {
    private DatabaseManager db;

    private Map<String, Integer> wordToId       = new HashMap<>();
    private Map<Integer, String> idToWord       = new HashMap<>();
    // Bigram followers: prev_id -> list of (next_id, count)
    private  Map<Integer, List<int[]>> bigramFollowers = new HashMap<>();
    // Trigram followers: (w1,w2) encoded in a long -> list of (w3_id, count)
    private  Map<Long, List<int[]>> trigramFollowers   = new HashMap<>();
    // Candidate sentence starts: (word_id, start_sentence_count)
    private final List<int[]> startCandidates          = new ArrayList<>();

    public GeneratorDataController(DatabaseManager db) throws SQLException {
        this.db = db;
        this.load();
        //startSequence
    }

    private void load() throws SQLException {
        wordToId = db.getWordIds();
        idToWord = db.getALlWordsAsString();
        bigramFollowers = db.getBigramMap();
        trigramFollowers = db.getTrigramMap();
    }


    /** Load bigram counts from word_pairs (prev -> next). */
    private void loadBigrams(Connection c) throws SQLException {
        String sql =
            "SELECT preceding_word_id, following_word_id, occurrence_count " +
            "FROM word_pairs";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int prev  = rs.getInt("preceding_word_id");
                int next  = rs.getInt("following_word_id");
                int count = rs.getInt("occurrence_count");

                bigramFollowers
                    .computeIfAbsent(prev, k -> new ArrayList<>())
                    .add(new int[]{ next, count });
            }
        }

        // sort each follower list by count desc for greedy
        for (List<int[]> list : bigramFollowers.values()) {
            list.sort((x, y) -> Integer.compare(y[1], x[1]));
        }
    }

    /** Load trigram counts from trigram table: (w1, w2) -> w3. */
    private void loadTrigrams(Connection c) throws SQLException {
        String sql =
            "SELECT first_word_id, second_word_id, third_word_id, follows_count " +
            "FROM trigram_sequence";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int w1    = rs.getInt("first_word_id");
                int w2    = rs.getInt("second_word_id");
                int w3    = rs.getInt("third_word_id");
                int count = rs.getInt("follows_count");

                long key = makePairKey(w1, w2);
                trigramFollowers
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new int[]{ w3, count });
            }
        }

        // sort each trigram follower list by count desc (for greedy)
        for (List<int[]> list : trigramFollowers.values()) {
            list.sort((a, b) -> Integer.compare(b[1], a[1]));
        }
    }

    private static long makePairKey(int w1, int w2) {
        return (((long) w1) << 32) | (w2 & 0xffffffffL);
    }

    // --- getters used by generators / factory ---

    public Map<String, Integer> getWordToId() {
        return wordToId;
    }

    public Map<Integer, String> getIdToWord() {
        return idToWord;
    }

    /** Bigram followers: prev_id -> list of (next_id, count). */
    public Map<Integer, List<int[]>> getBigramFollowers() {
        return bigramFollowers;
    }

    /** Trigram followers: (w1,w2) key -> list of (w3_id, count). */
    public Map<Long, List<int[]>> getTrigramFollowers() {
        return trigramFollowers;
    }

    public List<int[]> getStartCandidates() {
        return startCandidates;
    }
}

