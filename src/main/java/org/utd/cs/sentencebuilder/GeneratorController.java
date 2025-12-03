/**
 * Author: Kevin Tran (Refactored by Vincent Phan)
 * CS 4485 – Fall 2025
 * November 19, 2025
 *
 * Description:
 * A unified controller that manages:
 * 1. Data Loading: Caches words, IDs, bigrams, and trigrams from the DB.
 * 2. Logic Dispatch: Acts as a Factory to instantiate the correct
 * generation strategy (Greedy vs Weighted, Bigram vs Trigram).
 *
 * Usage:
 * GeneratorController gc = new GeneratorController(dbManager);
 * String result = gc.generate("bi_greedy", startingWordsList);
 */

package org.utd.cs.sentencebuilder;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class GeneratorController {
    private DatabaseManager db;

    // --- Cached Data Structures ---
    private Map<String, Integer> wordToId       = new HashMap<>();
    private Map<Integer, String> idToWord       = new HashMap<>();

    // Bigram followers: prev_id -> list of (next_id, count)
    private Map<Integer, List<int[]>> bigramFollowers = new HashMap<>();

    // Trigram followers: (w1,w2) encoded in a long -> list of (w3_id, count)
    private Map<Long, List<int[]>> trigramFollowers   = new HashMap<>();

    // Candidate sentence starts: (word_id, start_sentence_count)
    private final List<int[]> startCandidates         = new ArrayList<>();

    // 1. Map<WordID, [total, ends]>
    private Map<Integer, int[]> unigramStats = new HashMap<>();
    // 2. Map<PackedLong, [total, ends]>
    private Map<Long, int[]> bigramStats = new HashMap<>();
    // 3. Map<PackedLong, Map<WordID, [total, ends]>>
    private Map<Long, Map<Integer, int[]>> trigramStats = new HashMap<>();

    private Map<Integer, Double> lengthProbs = new HashMap<>();

    private ProbabilityEstimator estimator;
    private LogisticRegressionEOS model;
    private EosPredictor predictor;


    public GeneratorController(DatabaseManager db) throws SQLException, IOException {
        this.db = db;
        this.load();

        this.estimator = new ProbabilityEstimator(
                unigramStats,
                bigramStats,
                trigramStats,
                lengthProbs
        );

        File modelLoadPath = new File("data/model/model.json");
        this. model = LogisticRegressionEOS.loadModel(modelLoadPath);
        this.predictor = new EosPredictor(model, estimator);
    }

    /**
     * Loads all required data from the database into memory.
     */
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

    private long pack(int w1, int w2) {
        return ((long) w1 << 32) | (w2 & 0xFFFFFFFFL);
    }

    /**
     * Instantiates the correct algorithm and runs it immediately.
     * @param algo The algorithm key ("bi_greedy", "tri_weighted", etc.)
     * @param seed The user's input words
     * @return The generated sentence string
     */
    public String generate(String algo, List<String> seed) {
        SentenceGenerator generator;

        // Factory Logic moved internally
        switch (algo) {
            case "bi_weighted":
                generator = new BigramWeightedGenerator(
                        wordToId, idToWord, bigramFollowers, startCandidates, predictor
                );
                break;
            case "bi_greedy":
                generator = new BigramGreedyGenerator(
                        wordToId, idToWord, bigramFollowers, startCandidates, predictor
                );
                break;
            case "tri_weighted":
                generator = new TrigramWeightedGenerator(
                        wordToId, idToWord, trigramFollowers, startCandidates, predictor
                );
                break;
            case "tri_greedy":
            default:
                generator = new TrigramGreedyGenerator(
                        wordToId, idToWord, trigramFollowers, startCandidates, predictor
                );
                break;
        }

        return generator.generateSentence(seed, 1000, null);
    }
}
