/**
 *  Author: Kevin Tran
 *  CS4485. Fall 2025
 *  October 18 2025
 *  
 * Description:
 *  Implements a greedy bigram-based sentence generation algorithm that
 *  loads word and bigram frequency data from the MySQL database.
 *
 *  - Loads word and bigram frequency data from the MySQL database
 *  - Selects the most frequent following word greedily
 *  - Supports stop words and maximum token limits
 *  - Prevents short self-loops while allowing natural word reuse
 *
 *  Used for testing sentence generation in the RunGeneratorCli class.
 */


 package org.utd.cs.sentencebuilder;

import java.sql.*;
import java.util.*;

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
        return generateSentence(DEFAULT_MAX_TOKENS, null);
    }

    // previous signature with starting words; keeps behavior
    @Override
    public String generateSentence(List<String> startingWords) {
        return generateSentence(startingWords, DEFAULT_MAX_TOKENS, null);
    }

    // control length & optional stop word (case-insensitive)
    public String generateSentence(int maxTokens, String stopWord) {
        ensureLoaded();

        Integer startId = pickStartId();
        if (startId == null) return "";

        return buildGreedySentence(List.of(startId), maxTokens, stopWord);
    }

   // starting prompt + options
    public String generateSentence(List<String> startingWords, int maxTokens, String stopWord) {
        ensureLoaded();

        Integer startId = null;
        if (startingWords != null) {
            for (String w : startingWords) {
                if (w == null || w.isBlank()) continue;
                Integer id = wordToId.get(w.toLowerCase(Locale.ROOT));
                if (id != null) { startId = id; break; }
            }
        }
        if (startId == null) startId = pickStartId();
        if (startId == null) return "";

        return buildGreedySentence(List.of(startId), maxTokens, stopWord);
    }

    // ---------- internals ----------

    private void ensureLoaded() { if (!loaded) loadData(); }

    private Integer pickStartId() {
        if (!startCandidates.isEmpty()) return startCandidates.get(0)[0];
        return idToWord.isEmpty() ? null : idToWord.keySet().iterator().next();
    }

    private String buildGreedySentence(List<Integer> seed, int maxTokens, String stopWordRaw) {
        final String stopWord = (stopWordRaw == null) ? null : stopWordRaw.toLowerCase(Locale.ROOT);

        List<Integer> ids = new ArrayList<>(seed);
        Set<Integer> visited = new HashSet<>(ids);  // simple loop guard
        int curr = ids.get(ids.size() - 1);
        Integer last = null;                        // to detect immediate backtracks
        Set<Long> usedPairs = new HashSet<>();      // to track repeated bigrams

        // main loop
        while (ids.size() < Math.max(1, maxTokens)) {
            List<int[]> cands = followers.get(curr);
            if (cands == null || cands.isEmpty()) break;

            int nextId = -1;
            for (int[] cand : cands) {
                int candId = cand[0];
                // skip if this would cause immediate backtrack (A -> B -> A)
                if (last != null && candId == last) continue;
                // skip if we already used this bigram once in this sentence
                long pairKey = (((long) curr) << 32) | (candId & 0xffffffffL);
                if (usedPairs.contains(pairKey)) continue;

                nextId = candId;
                usedPairs.add(pairKey);
                break;
            }

            // if all candidates were filtered out, stop
            if (nextId == -1) break;

            // --- loop guards ---
            // self-loop (word -> word)
            if (nextId == curr) break;
         

            ids.add(nextId);
            visited.add(nextId);
            last = curr;
            curr = nextId;

            // user stop word (match after mapping id->word)
            if (stopWord != null) {
                String w = idToWord.getOrDefault(curr, "").toLowerCase(Locale.ROOT);
                if (w.equals(stopWord)) break;
            }
        }

        return render(ids);
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

    public String generateSentenceWithStop(String stopWord) {
        return generateSentence(DEFAULT_MAX_TOKENS, stopWord);
    }

    // NOTE: requires DatabaseManager to expose a public getConnection()
    private Connection getConnection() throws SQLException {
        return new DatabaseManager().getConnection();
    }
}