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

    public GeneratorController(DatabaseManager db) throws SQLException {
        this.db = db;
        this.load();
    }

    /**
     * Loads all required data from the database into memory.
     */
    private void load() throws SQLException {
        System.out.println("GeneratorController: Loading data...");

        // 1) word -> id map (normalized to lowercase)
        Map<String, Integer> rawWordIds = db.getWordIds();
        wordToId.clear();
        for (Map.Entry<String, Integer> e : rawWordIds.entrySet()) {
            wordToId.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }

        // 2) id -> word dictionary
        idToWord.clear();
        idToWord.putAll(db.getALlWordsAsString());

        // 3) bigram / trigram followers
        bigramFollowers.clear();
        bigramFollowers.putAll(db.getBigramMap());

        trigramFollowers.clear();
        trigramFollowers.putAll(db.getTrigramMap());

        // 4) build startCandidates
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

        System.out.println("GeneratorController: Data loaded successfully.");
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
                        wordToId, idToWord, bigramFollowers, startCandidates
                );
                break;
            case "bi_greedy":
                generator = new BigramGreedyGenerator(
                        wordToId, idToWord, bigramFollowers, startCandidates
                );
                break;
            case "tri_weighted":
                generator = new TrigramWeightedGenerator(
                        wordToId, idToWord, trigramFollowers, startCandidates
                );
                break;
            case "tri_greedy":
            default:
                generator = new TrigramGreedyGenerator(
                        wordToId, idToWord, trigramFollowers, startCandidates
                );
                break;
        }

        return generator.generateSentence(seed, 20, null);
    }
}
