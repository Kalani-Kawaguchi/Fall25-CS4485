package org.utd.cs.sentencebuilder;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProbabilityEstimator {
    private final DatabaseManager db;
    private final Map<Integer, Word> wordCache;

    // --- LRU caches for probability lookups ---
    private final Map<String, Double> trigramCache;
    private final Map<String, Double> bigramCache;

    public ProbabilityEstimator(DatabaseManager db, Map<Integer, Word> wordCache) {
        this(db, wordCache, 100_000); // default cache size
    }

    public ProbabilityEstimator(DatabaseManager db, Map<Integer, Word> wordCache, int cacheSize) {
        this.db = db;
        this.wordCache = wordCache;

        // Build simple LRU caches
        this.trigramCache = new LinkedHashMap<>(cacheSize, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Double> e) {
                return size() > cacheSize;
            }
        };
        this.bigramCache = new LinkedHashMap<>(cacheSize, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Double> e) {
                return size() > cacheSize;
            }
        };
    }

    public double pEosGivenWord(int id) throws SQLException {
        Word w = wordCache.get(id);
        if (w == null) return 0.5;
        return (w.getEndSequenceCount() + 1.0) / (w.getTotalOccurrences() + 2.0);
    }

    public double pEosGivenContext(Integer w1, Integer w2, Integer w3) throws SQLException {

        // 1. Try Trigram
        if (w1 != null && w2 != null && w3 != null) {
            String key = w1 + "_" + w2 + "_" + w3;
            // This will query the DB only if the key is not in the cache.
            double prob = trigramCache.computeIfAbsent(key, k -> {
                try {
                    return db.getTrigramEndProbability(w1, w2, w3);
                } catch (SQLException e) {
                    return 0.0;
                }
            });

            if (prob > 0.0) return prob;
        }

        // 2. Try Bigram (Backoff)
        if (w2 != null && w3 != null) {
            String key = w2 + "_" + w3;

            double prob = bigramCache.computeIfAbsent(key, k -> {
                try {
                    return db.getBigramEndProbability(w2, w3);
                } catch (SQLException e) {
                    return 0.0;
                }
            });

            if (prob > 0.0) return prob;
        }

        // 3. Try Unigram (Backoff)
        if (w3 != null) {
            double prob = this.pEosGivenWord(w3);
            if (prob > 0.0) return prob;
        }
        return 0.5; // Final fallback
    }

    public double pEosGivenLength(int length) throws SQLException {
        return db.getEosProbabilityGivenLength(length);
    }
}
