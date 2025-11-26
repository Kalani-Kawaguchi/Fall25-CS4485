/**
 * ProbabilityEstimator.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Vincent Phan
 * Date: November 6, 2025
 *
 * Description: Implements a hierarchical N-gram probability model to estimate
 * End-of-Sentence (EOS) likelihoods.
 *
 * This class models the probability distribution of sentence endings based on
 * historical text data.
 * All calculations apply Laplace smoothing to regularize counts and prevent
 * zero-probability errors for unseen data.
 */

package org.utd.cs.sentencebuilder;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProbabilityEstimator {
    //private final Map<Integer, Word> wordCache;
    //private final Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> trigramMap;
    //private final Map<Integer, Map<Integer, WordPair>> bigramMap;
    private final Map<Integer, Double> lengthProbs;

    // [0] = total occurrences, [1] = end sequence count
    private final Map<Integer, int[]> unigramStats;
    // Key: (w1 << 32 | w2) -> Value: [total, endCount]
    private final Map<Long, int[]> bigramStats;
    // Key: (w1 << 32 | w2) -> Value: Map<w3, [total, endCount]>
    // We nest the third word because 3 ints don't fit in one long.
    private final Map<Long, Map<Integer, int[]>> trigramStats;

    private final double globalEosPrior;
    private final double P_EOS_UNKNOWN_UNIGRAM;
    private final double P_EOS_UNKNOWN_LENGTH;

    public ProbabilityEstimator(Map<Integer, int[]> unigramStats,
                                Map<Long, int[]> bigramStats,
                                Map<Long, Map<Integer, int[]>> trigramStats,
                                Map<Integer, Double> lengthProbs) {
        this.unigramStats = unigramStats;
        this.bigramStats = bigramStats;
        this.trigramStats = trigramStats;
        this.lengthProbs = lengthProbs;

        // 1. Calculate totals by iterating over the cache
        long totalWords = 0;
        long totalSentences = 0;

        for (int[] stats : unigramStats.values()) {
            totalWords += stats[0];
            totalSentences += stats[1];
        }

        // 2. Calculate the baseline probability
        if (totalWords > 0) {
            this.globalEosPrior = (double) totalSentences / (double) totalWords;
        } else {
            // Assume 1 in 20 words ends a sentence which is pretty safe.
            this.globalEosPrior = 0.05;
        }

        // 3. Set the fallbacks using this calculated prior
        this.P_EOS_UNKNOWN_UNIGRAM = this.globalEosPrior;
        this.P_EOS_UNKNOWN_LENGTH = 0.01;
    }


    public double pEosGivenWord(int id) {
        int[] stats = unigramStats.get(id);
        if (stats == null) return P_EOS_UNKNOWN_UNIGRAM;
        return (stats[1] + 1.0) / (stats[0] + 2.0);
    }

    public double pEosGivenContext(Integer w1, Integer w2, Integer w3) {
        // 1. Try Trigram: Context is w1, w2 -> looking for w3 stats
        if (w1 != null && w2 != null && w3 != null) {
            long key = pack(w1, w2);
            Map<Integer, int[]> innerMap = trigramStats.get(key);
            if (innerMap != null) {
                int[] stats = innerMap.get(w3);
                if (stats != null) {
                    return (stats[1] + 1.0) / (stats[0] + 2.0);
                }
            }
        }

        // 2. Try Bigram: Context is w2 -> looking for w3 stats
        // Note: For EOS check, the "bigram" is the pair (w2, w3)
        if (w2 != null && w3 != null) {
            long key = pack(w2, w3);
            int[] stats = bigramStats.get(key);
            if (stats != null) {
                return (stats[1] + 1.0) / (stats[0] + 2.0);
            }
        }

        // 3. Try Unigram
        if (w3 != null) {
            return this.pEosGivenWord(w3);
        }

        return P_EOS_UNKNOWN_UNIGRAM;
    }

    public double pEosGivenLength(int length) {
        return lengthProbs.getOrDefault(length, P_EOS_UNKNOWN_LENGTH);
    }

    private long pack(int w1, int w2) {
        return ((long) w1 << 32) | (w2 & 0xFFFFFFFFL);
    }

}
