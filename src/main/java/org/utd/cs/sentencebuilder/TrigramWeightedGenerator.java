/**
 *  Author: Kevin Tran
 *  Modified for Trigrams: Renz Padlan
 *  CS4485. Fall 2025
 *  November 2025
 *
 *  Description:
 *  Implements a weighted random trigram-based sentence generation algorithm based of 
 *  the previous weighted random bigram algorithm
 *
 *  - Now Loads all words and trigrams from the database (via DatabaseManager)
 *  - Chooses a random start word weighted by start_sentence_count
 *  - For each TWO-word sequence, selects the next word based on weighted probability (occurrence_count)
 *  - Stops after max token limit or when no valid follower exists
 *  - Provides better context and more varied output than bigram weighted model
 */

package org.utd.cs.sentencebuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class TrigramWeightedGenerator implements SentenceGenerator {    // Bigram -> Trigram
    
    private static final int DEFAULT_MAX_TOKENS = 20;
    private final Random random = new Random();

    private final Map<String, Integer> wordToId = new HashMap<>();
    private final Map<Integer, String> idToWord = new HashMap<>();
    
    // Bigram -> Trigram (word1_id, word2_id) encoded as long -> list of (following_id, count)
    private final Map<Long, List<int[]>> followers = new HashMap<>();
    
    private final List<int[]> startCandidates = new ArrayList<>();

    private boolean loaded = false;

    @Override
    public String getName() {
        return "TrigramWeightedGenerator";      
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

            // Bigram -> Trigram. Load all trigrams from word_triplets table
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT word1_id, word2_id, word3_id, occurrence_count FROM word_triplets")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int word1 = rs.getInt("word1_id");
                        int word2 = rs.getInt("word2_id");
                        int word3 = rs.getInt("word3_id");
                        int count = rs.getInt("occurrence_count");
                        
                        // Create composite key from TWO word IDs
                        long pairKey = makePairKey(word1, word2);
                        
                        followers.computeIfAbsent(pairKey, k -> new ArrayList<>())
                                .add(new int[]{word3, count});
                    }
                }
            }

            loaded = true;
            System.out.println("✅ TrigramWeightedGenerator: Data loaded successfully.");
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

        // Bigram -> Trigram, need TWO starting words
        List<Integer> startIds = pickWeightedStartIds(2);
        if (startIds.size() < 2) return "";

        return buildSentence(startIds, maxTokens, stopWord);
    }

    public String generateSentence(List<String> startingWords, int maxTokens, String stopWord) {
        ensureLoaded();

        List<Integer> startIds = new ArrayList<>();
        
        if (startingWords != null) {
            for (String w : startingWords) {
                if (w == null || w.isBlank()) continue;
                Integer id = wordToId.get(w.toLowerCase(Locale.ROOT));
                if (id != null) {
                    startIds.add(id);
                    if (startIds.size() >= 2) break;  // We need exactly 2 for trigram
                }
            }
        }
        
        // If we don't have 2 starting words, fill in with weighted random starts
        while (startIds.size() < 2) {
            Integer nextId = pickWeightedStartId();
            if (nextId == null) return "";
            // Avoid duplicates in start sequence
            if (!startIds.contains(nextId)) {
                startIds.add(nextId);
            } else if (startCandidates.size() > startIds.size()) {
                // Try to pick a different word
                continue;
            } else {
                startIds.add(nextId); // Give up and allow duplicate
            }
        }

        return buildSentence(startIds, maxTokens, stopWord);
    }

    // ---------- internals ----------

    private void ensureLoaded() { if (!loaded) loadData(); }

    private Integer pickWeightedStartId() {
        if (startCandidates.isEmpty()) {
            return idToWord.isEmpty() ? null : idToWord.keySet().iterator().next();
        }

        int total = startCandidates.stream().mapToInt(a -> a[1]).sum();
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int[] c : startCandidates) {
            cumulative += c[1];
            if (roll < cumulative) return c[0];
        }
        return startCandidates.get(0)[0]; // fallback
    }

    
     // Pick multiple weighted random start words for trigram initialization
     
    private List<Integer> pickWeightedStartIds(int count) {
        List<Integer> ids = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        
        for (int i = 0; i < count; i++) {
            // Try to pick a unique start word
            int attempts = 0;
            while (attempts < 10) {  // Limit attempts to avoid infinite loop
                Integer id = pickWeightedStartId();
                if (id == null) break;
                
                if (!used.contains(id)) {
                    ids.add(id);
                    used.add(id);
                    break;
                } else {
                    attempts++;
                }
            }
            
            // If we couldn't find a unique word, just add what we got
            if (ids.size() <= i) {
                Integer id = pickWeightedStartId();
                if (id != null) ids.add(id);
            }
        }
        
        return ids;
    }

    
     // Build sentence using weighted random algorithm implementing trigrams
    private String buildSentence(List<Integer> seed, int maxTokens, String stopWordRaw) {
        if (seed.size() < 2) return "";
        
        final String stopWord = (stopWordRaw == null) ? null : stopWordRaw.toLowerCase(Locale.ROOT);
        
        List<Integer> ids = new ArrayList<>(seed);
        Set<Integer> visited = new HashSet<>(ids);
        
        // Bigrams -> Trigrams since we are tracking last 2 words
        int secondLast = ids.get(ids.size() - 2);
        int last = ids.get(ids.size() - 1);

        // main loop
        // Bigram -> Trigram 
        while (ids.size() < Math.max(2, maxTokens)) {
            // Look up followers using TWO-word context
            long pairKey = makePairKey(secondLast, last);
            List<int[]> cands = followers.get(pairKey);
            
            if (cands == null || cands.isEmpty()) break;

            // Choose next word using weighted randomness
            int nextId = chooseWeightedNext(cands);
            
            // Simple loop prevention: don't revisit words
            if (visited.contains(nextId)) break;

            ids.add(nextId);
            visited.add(nextId);
            
            // Shift the two-word window
            secondLast = last;
            last = nextId;

            // Check for stop word
            if (stopWord != null) {
                String w = idToWord.getOrDefault(last, "").toLowerCase(Locale.ROOT);
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

    // Bigram -> Trigram, Encoding two word IDs into a single long for use as map key
    private long makePairKey(int word1Id, int word2Id) {
        return (((long) word1Id) << 32) | (word2Id & 0xffffffffL);
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