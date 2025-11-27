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

    // 1. Map<WordID, [total, ends]>
    private Map<Integer, int[]> unigramStats = new HashMap<>();
    // 2. Map<PackedLong, [total, ends]>
    private Map<Long, int[]> bigramStats = new HashMap<>();
    // 3. Map<PackedLong, Map<WordID, [total, ends]>>
    private Map<Long, Map<Integer, int[]>> trigramStats = new HashMap<>();

    private Map<Integer, Double> lengthProbs = new HashMap<>();


    public GeneratorDataController(DatabaseManager db) throws SQLException {
        this.db = db;
        this.load();
        //startSequence
    }

    private void load() throws SQLException {
        // 1. Load Words
        // We use the full object map to populate: IDs, Strings, StartCandidates, and UnigramStats
        Map<Integer, Word> allWords = db.getAllWords();

        wordToId.clear();
        idToWord.clear();
        startCandidates.clear();
        unigramStats.clear();

        for (Word w : allWords.values()) {
            int id = w.getWordId();
            String text = w.getWordValue(); // Assuming Word has a getText() method

            if (text != null) {
                wordToId.put(text.toLowerCase(Locale.ROOT), id);
                idToWord.put(id, text);
            }

            // Populate Start Candidates
            if (w.getStartSentenceCount() > 0) {
                startCandidates.add(new int[]{ id, w.getStartSentenceCount() });
            }

            unigramStats.put(id, new int[]{ w.getTotalOccurrences(), w.getEndSequenceCount() });
        }

        // Sort start candidates by frequency desc
        startCandidates.sort((a, b) -> Integer.compare(b[1], a[1]));


        // 2. Load Bigrams (WordPairs)
        Map<Integer, Map<Integer, WordPair>> allPairs = db.getAllWordPairs();

        bigramFollowers.clear();
        bigramStats.clear();

        for (Map.Entry<Integer, Map<Integer, WordPair>> outer : allPairs.entrySet()) {
            int w1 = outer.getKey();
            List<int[]> followers = new ArrayList<>();

            for (Map.Entry<Integer, WordPair> inner : outer.getValue().entrySet()) {
                int w2 = inner.getKey();
                WordPair wp = inner.getValue();

                // For Estimator: Packed Key -> Stats
                long key = pack(w1, w2);
                bigramStats.put(key, new int[]{ wp.getOccurrenceCount(), wp.getEndFrequency() });

                // For Generator: Add to follower list
                followers.add(new int[]{ w2, wp.getOccurrenceCount() });
            }

            // Sort followers by count desc for Greedy/Beam search optimization
            followers.sort((a, b) -> Integer.compare(b[1], a[1]));
            bigramFollowers.put(w1, followers);
        }


        // 3. Load Trigrams (WordTriplets)
        Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> allTriplets = db.getAllWordTriplets();

        trigramFollowers.clear();
        trigramStats.clear();

        for (var level1 : allTriplets.entrySet()) {
            int w1 = level1.getKey();
            for (var level2 : level1.getValue().entrySet()) {
                int w2 = level2.getKey();
                long key = pack(w1, w2);

                List<int[]> followers = new ArrayList<>();
                // Ensure the inner map exists for stats
                trigramStats.putIfAbsent(key, new HashMap<>());
                Map<Integer, int[]> innerStats = trigramStats.get(key);

                for (var level3 : level2.getValue().entrySet()) {
                    int w3 = level3.getKey();
                    WordTriplet wt = level3.getValue();

                    // For Estimator
                    innerStats.put(w3, new int[]{ wt.getOccurrenceCount(), wt.getEndFrequency() });

                    // For Generator
                    followers.add(new int[]{ w3, wt.getOccurrenceCount() });
                }

                // Sort followers
                followers.sort((a, b) -> Integer.compare(b[1], a[1]));
                trigramFollowers.put(key, followers);
            }
        }

        // 4. Load Length Probabilities
        this.lengthProbs = db.getLengthProbabilityMap();

        allWords = null;
        allPairs = null;
        allTriplets = null;
        System.gc();
    }
    // old loading method
    /**
    private void load() throws SQLException {
        // 1) word -> id map (normalized to lowercase for lookups)
        Map<String, Integer> rawWordIds = db.getWordIds();
        wordToId.clear();
        for (Map.Entry<String, Integer> e : rawWordIds.entrySet()) {
            String lower = e.getKey().toLowerCase(Locale.ROOT);
            wordToId.put(lower, e.getValue());
        }

        // 2) id -> word dictionary
        idToWord.clear();
        idToWord.putAll(db.getALlWordsAsString());

        // 3) bigram / trigram followers
        bigramFollowers.clear();
        bigramFollowers.putAll(db.getBigramMap());

        trigramFollowers.clear();
        trigramFollowers.putAll(db.getTrigramMap());

        // 4) build startCandidates from full Word objects
        startCandidates.clear();
        Map<Integer, Word> allWords = db.getAllWords();
        for (Word w : allWords.values()) {
            int start = w.getStartSentenceCount();
            if (start > 0) {
                startCandidates.add(new int[]{ w.getWordId(), start });
            }
        }
        // sort by start_sentence_count desc
        startCandidates.sort((a, b) -> Integer.compare(b[1], a[1]));
    }
     **/

    private long pack(int w1, int w2) {
        return ((long) w1 << 32) | (w2 & 0xFFFFFFFFL);
    }
    
    /**
     * Resolve a list of seed words into word IDs, up to neededCount.
     * - Matches case-insensitively using wordToId.
     * - If we don't get enough IDs from the seeds, we top up with
     *   the most common start-of-sentence words.
     */
    public List<Integer> resolveSeedWords(List<String> startingWords, int neededCount) {
        List<Integer> ids = new ArrayList<>();

        if (startingWords != null) {
            for (String w : startingWords) {
                if (w == null || w.isBlank()) continue;
                Integer id = wordToId.get(w.toLowerCase(Locale.ROOT));
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                    if (ids.size() >= neededCount) break;
                }
            }
        }

        // Top up with common start words if necessary
        int idx = 0;
        while (ids.size() < neededCount && idx < startCandidates.size()) {
            int candidateId = startCandidates.get(idx)[0];
            if (!ids.contains(candidateId)) {
                ids.add(candidateId);
            }
            idx++;
        }

        // Final fallback: grab any remaining word IDs if still short
        if (ids.size() < neededCount && !idToWord.isEmpty()) {
            for (Integer id : idToWord.keySet()) {
                if (!ids.contains(id)) {
                    ids.add(id);
                    if (ids.size() >= neededCount) break;
                }
            }
        }

        return ids;
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

    public Map<Integer, int[]> getUnigramStats() {
        return unigramStats;
    }

    public Map<Long, int[]> getBigramStats() {
        return bigramStats;
    }

    public Map<Long, Map<Integer, int[]>> getTrigramStats() {
        return trigramStats;
    }

    public Map<Integer, Double> getLengthProbs() {
        return lengthProbs;
    }
}

