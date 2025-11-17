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
    private final Map<Integer, Word> wordCache;
    private final Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> trigramMap;
    private final Map<Integer, Map<Integer, WordPair>> bigramMap;
    private final Map<Integer, Double> lengthProbs;

    private final double globalEosPrior;
    private final double P_EOS_UNKNOWN_UNIGRAM;
    private final double P_EOS_UNKNOWN_LENGTH;

    public ProbabilityEstimator(Map<Integer, Word> wordCache,
                                Map<Integer, Map<Integer, WordPair>> bigramMap,
                                Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> trigramMap,
                                Map<Integer, Double> lengthProbs) {
        this.wordCache = wordCache;
        this.bigramMap = bigramMap;
        this.trigramMap = trigramMap;
        this.lengthProbs = lengthProbs;

        // 1. Calculate totals by iterating over the cache
        long totalWords = 0;
        long totalSentences = 0;

        for (Word w : wordCache.values()) {
            totalWords += w.getTotalOccurrences();
            totalSentences += w.getEndSequenceCount();
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
        Word w = wordCache.get(id);
        if (w == null) return P_EOS_UNKNOWN_UNIGRAM;
        return (w.getEndSequenceCount() + 1.0) / (w.getTotalOccurrences() + 2.0);
    }

    public double pEosGivenContext(Integer w1, Integer w2, Integer w3) {

        // 1. Try Trigram
        if (w1 != null && w2 != null && w3 != null) {
            Map<Integer, Map<Integer, WordTriplet>> midMap = trigramMap.get(w1);
            if (midMap != null) {
                Map<Integer, WordTriplet> innerMap = midMap.get(w2);
                if (innerMap != null) {
                    WordTriplet triplet = innerMap.get(w3);
                    if (triplet != null) {
                        // Found triplet, calculate probability
                        return (triplet.getEndFrequency() + 1.0) / (triplet.getOccurrenceCount() + 2.0);
                    }
                }
            }
        }

        // 2. Try Bigram (Backoff)
        if (w2 != null && w3 != null) {
            Map<Integer, WordPair> innerMap = bigramMap.get(w2);
            if (innerMap != null) {
                WordPair pair = innerMap.get(w3);
                if (pair != null) {
                    // Found bigram, calculate probability
                    return (pair.getEndFrequency() + 1.0) / (pair.getOccurrenceCount() + 2.0);
                }
            }
        }

        // 3. Try Unigram (Backoff)
        if (w3 != null) {
            return this.pEosGivenWord(w3);
        }

        return P_EOS_UNKNOWN_UNIGRAM; // Final fallback
    }

    public double pEosGivenLength(int length) {
        return lengthProbs.getOrDefault(length, P_EOS_UNKNOWN_LENGTH);
    }
}
