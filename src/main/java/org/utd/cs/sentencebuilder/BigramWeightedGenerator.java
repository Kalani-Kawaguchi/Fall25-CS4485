/**
 *  Author: Kevin Tran
 *  CS4485. Fall 2025
 *  November 2025
 *
 *  Description:
 *  Implements a weighted random bigram-based sentence generation algorithm.
 *
 *  - Loads all words and bigrams from the database (via DatabaseManager)
 *  - Chooses a random start word weighted by start_sentence_count
 *  - For each word, selects the next word based on weighted probability (occurrence_count)
 *  - Stops after max token limit or when no valid follower exists
 */

package org.utd.cs.sentencebuilder;

import java.sql.*;
import java.util.*;

public class BigramWeightedGenerator implements SentenceGenerator {

    private static final int DEFAULT_MAX_TOKENS = 20;
    private final Random random = new Random();

    private final Map<String, Integer> wordToId = new HashMap<>();
    private final Map<Integer, String> idToWord = new HashMap<>();
    private final Map<Integer, List<int[]>> followers = new HashMap<>();
    private final List<int[]> startCandidates = new ArrayList<>();

    private boolean loaded = false;

    @Override
    public String getName() {
        return "BigramWeightedGenerator";
    }

    @Override
    public void loadData() {
        try (Connection c = getConnection()) {
            // Load all words
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT word_id, word_value, start_sentence_count FROM words")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("word_id");
                        String val = rs.getString("word_value");
                        int start = rs.getInt("start_sentence_count");

                        wordToId.put(val, id);
                        idToWord.put(id, val);
                        if (start > 0) startCandidates.add(new int[]{id, start});
                    }
                }
            }

            // Load all bigrams
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT preceding_word_id, following_word_id, occurrence_count FROM word_pairs")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int prev = rs.getInt("preceding_word_id");
                        int next = rs.getInt("following_word_id");
                        int count = rs.getInt("occurrence_count");
                        followers.computeIfAbsent(prev, k -> new ArrayList<>())
                                .add(new int[]{next, count});
                    }
                }
            }

            loaded = true;
            System.out.println("✅ BigramWeightedGenerator: Data loaded successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            loaded = false;
        }
    }

    @Override
    public String generateSentence() {
        return generateSentence(DEFAULT_MAX_TOKENS, null);
    }

    @Override
    public String generateSentence(List<String> startingWords) {
        return generateSentence(startingWords, DEFAULT_MAX_TOKENS, null);
    }

    public String generateSentence(int maxTokens, String stopWord) {
        ensureLoaded();

        Integer startId = pickWeightedStartId();
        if (startId == null) return "";

        return buildSentence(List.of(startId), maxTokens, stopWord);
    }

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
        if (startId == null) startId = pickWeightedStartId();
        if (startId == null) return "";

        return buildSentence(List.of(startId), maxTokens, stopWord);
    }

    // ---------- internals ----------

    private void ensureLoaded() { if (!loaded) loadData(); }

    private Integer pickWeightedStartId() {
        if (startCandidates.isEmpty()) return idToWord.isEmpty() ? null : idToWord.keySet().iterator().next();

        int total = startCandidates.stream().mapToInt(a -> a[1]).sum();
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int[] c : startCandidates) {
            cumulative += c[1];
            if (roll < cumulative) return c[0];
        }
        return startCandidates.get(0)[0]; // fallback
    }

    private String buildSentence(List<Integer> seed, int maxTokens, String stopWordRaw) {
        final String stopWord = (stopWordRaw == null) ? null : stopWordRaw.toLowerCase(Locale.ROOT);
        List<Integer> ids = new ArrayList<>(seed);
        Set<Integer> visited = new HashSet<>(ids);
        int curr = ids.get(ids.size() - 1);

        while (ids.size() < Math.max(1, maxTokens)) {
            List<int[]> cands = followers.get(curr);
            if (cands == null || cands.isEmpty()) break;

            int nextId = chooseWeightedNext(cands);
            if (visited.contains(nextId)) break;

            ids.add(nextId);
            visited.add(nextId);
            curr = nextId;

            if (stopWord != null) {
                String w = idToWord.getOrDefault(curr, "").toLowerCase(Locale.ROOT);
                if (w.equals(stopWord)) break;
            }
        }

        return render(ids);
    }

    // --- Core weighted randomness logic ---
    private int chooseWeightedNext(List<int[]> cands) {
        int total = 0;
        for (int[] c : cands) total += c[1];
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int[] c : cands) {
            cumulative += c[1];
            if (roll < cumulative) return c[0];
        }
        return cands.get(0)[0]; // fallback
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
