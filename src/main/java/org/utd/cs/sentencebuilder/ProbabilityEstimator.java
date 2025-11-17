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

        // A smarter version could calculate the average P(EOS) from the maps
        this.P_EOS_UNKNOWN_UNIGRAM = 0.5;
        this.P_EOS_UNKNOWN_LENGTH = 0.5;
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
