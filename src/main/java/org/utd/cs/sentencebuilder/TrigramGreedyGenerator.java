/**
 *  Author: Kevin Tran
 *  Modified for Trigrams: Renz Padlan
 *  CS4485. Fall 2025
 *  November 2025
 *  
 * Description:
 *  Implements a greedy Trigram-based sentence generation algorithm based of
 *  the previous greedy Bigram-based sentence generation algorithm that now
 *  loads word and bigram frequency data from the MySQL database.
 *
 *  - Loads word and trigram frequency data from the MySQL database
 *  - Selects the most frequent following word greedily
 *  - Supports stop words and maximum token limits
 *  - Prevents short self-loops while allowing natural word reuse
 *
 *  Used for testing sentence generation in the RunGeneratorCli class.
 * 
 */


 package org.utd.cs.sentencebuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Greedy trigram generator:
 * - loadData(): caches words and bigram followers in memory
 * - generateSentence(): picks a likely start word, then always chooses the most frequent follower
 * - generateSentence(List<String>): starts from your provided word(s) if present, same greedy step
 */
public class TrigramGreedyGenerator implements SentenceGenerator {

    // Tunables
    private static final int DEFAULT_MAX_TOKENS = 20;

    // In-memory caches after loadData()
    private final Map<String, Integer> wordToId = new HashMap<>();
    private final Map<Integer, String> idToWord = new HashMap<>();

    // Bigram -> Trigram (word1_id, word2_id) -> sorted list of (following_id, count) DESC
    private final Map<Long, List<int[]>> followers = new HashMap<>();

    // Precomputed start candidates (id -> start_sentence_count); used to choose a start when not provided
    private final List<int[]> startCandidates = new ArrayList<>();

    private boolean loaded = false;

    @Override
    public String getName() {
        return "TrigramGreedyGenerator";
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

            // Bigram -> Trigram 2) Load trigram followers from word_triplets table
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT word1_id, word2_id, word3_id, occurrence_count " +
                    "FROM word_triplets")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int word1 = rs.getInt("word1_id");
                        int word2 = rs.getInt("word2_id");
                        int word3 = rs.getInt("word3_id");                        
                        int cnt  = rs.getInt("occurrence_count");

                        // Create composite key from two word IDs
                        long pairkey = makePairKey(word1, word2);

                        followers.computeIfAbsent(pairkey, k -> new ArrayList<>())
                                 .add(new int[]{word3, cnt});
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

        // Bigram -> Trigram, need TWO starting words
        List<Integer> startIds = pickStartIds(2);
        if (startIds.size() < 2) return "";

        return buildGreedySentence(startIds, maxTokens, stopWord);
    }

   // starting prompt + options
    public String generateSentence(List<String> startingWords, int maxTokens, String stopWord) {
        ensureLoaded();

        // Trigram, ensuring word1 and word2 are used to start
        List<Integer> startIds = new ArrayList<>();

        if (startingWords != null) {
            for (String w : startingWords) {
                if (w == null || w.isBlank()) continue;
                Integer id = wordToId.get(w.toLowerCase(Locale.ROOT));
                if (id != null) {
                    startIds.add(id);
                    if (startIds.size() >=2) break;
                }
            }
        }
        
        // If there is no 2 starting words, fill with most common starts
        while (startIds.size() < 2) {
            Integer nextId = pickStartId(startIds.size());
            if (nextId == null) return "";
            startIds.add(nextId);
        }

        /*  Bigram 
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
        */

        return buildGreedySentence(startIds, maxTokens, stopWord);
 
    }
    // ---------- internals ----------

    private void ensureLoaded() { if (!loaded) loadData(); }

    // Bigram -> Trigram 
    private Integer pickStartId(int index) {
        if (index < startCandidates.size()) {
            return startCandidates.get(index)[0];
        }
        if (!idToWord.isEmpty()) {
            // Fallback to any word
            return idToWord.keySet().iterator().next();
        }
        return null;
    }

    
     // Bigram -> Trigram, Pick multiple start word IDs
    private List<Integer> pickStartIds(int count) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < count && i < startCandidates.size(); i++) {
            ids.add(startCandidates.get(i)[0]);
        }
        // If we don't have enough start candidates, add any words
        if (ids.size() < count) {
            Iterator<Integer> iter = idToWord.keySet().iterator();
            while (ids.size() < count && iter.hasNext()) {
                Integer next = iter.next();
                if (!ids.contains(next)) {
                    ids.add(next);
                }
            }
        }
        return ids;
    }


     // Build a sentence using trigram greedy algorithm
     // Requires at least 2 starting word IDs

    private String buildGreedySentence(List<Integer> seed, int maxTokens, String stopWordRaw) {
        if (seed.size() < 2) return "";

        final String stopWord = (stopWordRaw == null) ? null : stopWordRaw.toLowerCase(Locale.ROOT);

        List<Integer> ids = new ArrayList<>(seed);
        Set<Integer> visited = new HashSet<>(ids);  // simple loop guard

        // Bigrams -> Trigrams since we are tracking last 2 words
        int secondLast = ids.get(ids.size() - 2);
        int last = ids.get(ids.size() - 2);
        
        Set<String> usedTrigrams = new HashSet<>();

        // main loop
        // Bigram -> Trigram 
        while (ids.size() < Math.max(2, maxTokens)) {
            long pairKey = makePairKey(secondLast, last);
            List<int[]> cands = followers.get(pairKey);
            
            if (cands == null || cands.isEmpty()) {
                // no trigram found, fall back to bigram here
                break;
            }
            int nextId = -1;
            for (int[] cand : cands) {
                int candId = cand[0];

                // Bigram -> trigram, create signature for duplicate detection
                String trigramSig = secondLast + "-" + last + "-" + candId;
                if (usedTrigrams.contains(trigramSig)) continue;
                
                // Prevent immediate self-loops (word -> word)
                if (candId == last) continue;
                
                // Prevent three-word loops (A B A pattern)
                if (candId == secondLast) continue;

                nextId = candId;
                usedTrigrams.add(trigramSig);
                break;                
            }

            // if all candidates were filtered out, stop
            if (nextId == -1) break;

            ids.add(nextId);
            visited.add(nextId);

            // Bigram -> Trigram, Shift the window
            secondLast = last;
            last = nextId;
            

            // user stop word (match after mapping id->word)
            if (stopWord != null) {
                String w = idToWord.getOrDefault(last, "").toLowerCase(Locale.ROOT);
                if (w.equals(stopWord)) break;
            }
        }

        return render(ids);
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

    public String generateSentenceWithStop(String stopWord) {
        return generateSentence(DEFAULT_MAX_TOKENS, stopWord);
    }

    // NOTE: requires DatabaseManager to expose a public getConnection()
    private Connection getConnection() throws SQLException {
        return new DatabaseManager().getConnection();
    }
}