/**
 *  Author: Kevin Tran
 *  CS4485. Fall 2025
 *  October 18 2025
 *  
 * Description:
 *  Implements a greedy bigram-based sentence generation algorithm that
 *  loads word and bigram frequency data from the MySQL database.
 *
 *  - Chooses a weighted random start word based on start_sentence_count
 *  - For each word, selects the most frequent following word (greedy)
 *  - Stops after a maximum token limit or when no valid follower exists
 *
 *  Used for testing sentence generation in the RunGeneratorCli class.
 */


 package org.utd.cs.sentencebuilder;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Greedy bigram generator:
 * - loadData(): caches words and bigram followers in memory
 * - generateSentence(): picks a likely start word, then always chooses the most frequent follower
 * - generateSentence(List<String>): starts from your provided word(s) if present, same greedy step
 */
public class BigramGreedyGenerator implements SentenceGenerator {

    // Tunables
    private static final int DEFAULT_MAX_TOKENS = 20;

    // In-memory caches after loadData()
    private final Map<String, Integer> wordToId = new HashMap<>();
    private final Map<Integer, String> idToWord = new HashMap<>();
    // preceding_id -> sorted list of (following_id, count) DESC
    private final Map<Integer, List<int[]>> followers = new HashMap<>();

    // Precomputed start candidates (id -> start_sentence_count); used to choose a start when not provided
    private final List<int[]> startCandidates = new ArrayList<>();

    private boolean loaded = false;

    @Override
    public String getName() {
        return "BigramGreedyGenerator";
    }

    @Override
    public void loadData() {
        try (Connection c = getConnection()) {
            // 1) Load words (ids, strings, starts)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT word_id, word_value, start_sentence_count, total_occurrences " +
                    "FROM words")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("word_id");
                        String val = rs.getString("word_value");
                        int start = rs.getInt("start_sentence_count");
                        // int total = rs.getInt("total_occurrences"); // available if you want backoff/unigram
                        wordToId.put(val, id);
                        idToWord.put(id, val);
                        if (start > 0) startCandidates.add(new int[]{id, start});
                    }
                }
            }

            // sort start candidates by start_sentence_count desc, then by total_occurrences if desired
            startCandidates.sort((a,b) -> Integer.compare(b[1], a[1]));

            // 2) Load bigram followers
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT preceding_word_id, following_word_id, occurrence_count " +
                    "FROM word_pairs")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int prev = rs.getInt("preceding_word_id");
                        int next = rs.getInt("following_word_id");
                        int cnt  = rs.getInt("occurrence_count");
                        followers.computeIfAbsent(prev, k -> new ArrayList<>())
                                 .add(new int[]{next, cnt});
                    }
                }
            }

            // sort each follower list by count desc (greedy = top first)
            for (var e : followers.entrySet()) {
                e.getValue().sort((x, y) -> Integer.compare(y[1], x[1]));
            }

            loaded = true;
        } catch (SQLException e) {
            e.printStackTrace();
            loaded = false;
        }
    }

    @Override
    public String generateSentence() {
        ensureLoaded();
        // pick best start: highest start_sentence_count (fallback to most frequent word if empty)
        Integer startId = pickStartId();
        if (startId == null) return "";

        List<Integer> ids = new ArrayList<>();
        ids.add(startId);

        int curr = startId;
        for (int i = 1; i < DEFAULT_MAX_TOKENS; i++) {
            List<int[]> cands = followers.get(curr);
            if (cands == null || cands.isEmpty()) break;
            int nextId = cands.get(0)[0]; // greedy: most frequent follower
            ids.add(nextId);
            curr = nextId;
        }
        return render(ids);
    }

    @Override
    public String generateSentence(List<String> startingWords) {
        ensureLoaded();
        if (startingWords == null || startingWords.isEmpty()) {
            return generateSentence();
        }

        // Use the first provided word that exists in vocab; ignore others for this bigram-only version
        Integer startId = null;
        for (String w : startingWords) {
            if (w == null || w.isBlank()) continue;
            Integer id = wordToId.get(w.toLowerCase(Locale.ROOT));
            if (id != null) { startId = id; break; }
        }
        if (startId == null) {
            // fallback to automatic starter
            startId = pickStartId();
            if (startId == null) return "";
        }

        List<Integer> ids = new ArrayList<>();
        ids.add(startId);

        int curr = startId;
        for (int i = 1; i < DEFAULT_MAX_TOKENS; i++) {
            List<int[]> cands = followers.get(curr);
            if (cands == null || cands.isEmpty()) break;
            int nextId = cands.get(0)[0]; // greedy
            ids.add(nextId);
            curr = nextId;
        }
        return render(ids);
    }

    // --- helpers ---

    private void ensureLoaded() {
        if (!loaded) loadData();
    }

    // pick a start word, preferring words that actually start sentences in the corpus
    private Integer pickStartId() {
        // If we captured starters (id, startCount), pick weighted by startCount among the top K
        if (!startCandidates.isEmpty()) {
            // Limit to top K to avoid overweighting super-long tails
            final int K = Math.min(50, startCandidates.size());

            long total = 0;
            for (int i = 0; i < K; i++) total += Math.max(1, startCandidates.get(i)[1]);

            long r = ThreadLocalRandom.current().nextLong(1, total + 1);
            long cum = 0;
            for (int i = 0; i < K; i++) {
                cum += Math.max(1, startCandidates.get(i)[1]);
                if (r <= cum) return startCandidates.get(i)[0];
            }
            return startCandidates.get(0)[0]; // safety
        }

        // Fallback: pick any word present
        return idToWord.isEmpty() ? null : idToWord.keySet().iterator().next();
    }


    private String render(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            String w = idToWord.getOrDefault(id, "?");
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }

    private Connection getConnection() throws SQLException {
        return new DatabaseManager().getConnection();
    }
}
