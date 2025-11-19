package org.utd.cs.sentencebuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Loads and caches all data needed for bigram-based generators:
 *  - wordToId / idToWord dictionaries
 *  - followers: preceding_word_id -> list of (following_word_id, count), sorted by count desc
 *  - startCandidates: (word_id, start_sentence_count), sorted by start_sentence_count desc
 *
 * This lets multiple generator strategies share the same in-memory data.
 */
public class GeneratorDataController {

    private final Map<String, Integer> wordToId = new HashMap<>();
    private final Map<Integer, String> idToWord = new HashMap<>();
    private final Map<Integer, List<int[]>> followers = new HashMap<>();
    private final List<int[]> startCandidates = new ArrayList<>();

    public GeneratorDataController(DatabaseManager db) throws SQLException {
        try (Connection c = db.getConnection()) {
            loadWords(c);
            loadFollowers(c);
        }
    }

    private void loadWords(Connection c) throws SQLException {
        String sql = "SELECT word_id, word_value, start_sentence_count " +
                     "FROM words";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("word_id");
                String val = rs.getString("word_value");
                int start = rs.getInt("start_sentence_count");

                wordToId.put(val, id);
                idToWord.put(id, val);
                if (start > 0) {
                    startCandidates.add(new int[]{id, start});
                }
            }
        }

        // sort start candidates by start_sentence_count desc
        startCandidates.sort((a, b) -> Integer.compare(b[1], a[1]));
    }

    private void loadFollowers(Connection c) throws SQLException {
        String sql = "SELECT preceding_word_id, following_word_id, occurrence_count " +
                     "FROM word_pairs";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int prev = rs.getInt("preceding_word_id");
                int next = rs.getInt("following_word_id");
                int cnt  = rs.getInt("occurrence_count");

                followers
                    .computeIfAbsent(prev, k -> new ArrayList<>())
                    .add(new int[]{next, cnt});
            }
        }

        // sort each follower list by count desc
        for (var e : followers.entrySet()) {
            e.getValue().sort((x, y) -> Integer.compare(y[1], x[1]));
        }
    }

    // --- getters used by generators / factory ---

    public Map<String, Integer> getWordToId() {
        return wordToId;
    }

    public Map<Integer, String> getIdToWord() {
        return idToWord;
    }

    public Map<Integer, List<int[]>> getFollowers() {
        return followers;
    }

    public List<int[]> getStartCandidates() {
        return startCandidates;
    }
}
