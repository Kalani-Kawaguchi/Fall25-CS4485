package org.utd.cs.sentencebuilder;

import java.sql.SQLException;
import java.util.Map;

public class ProbabilityEstimator {
    private final DatabaseManager db;
    private final Map<Integer, Word> wordCache;

    public ProbabilityEstimator(DatabaseManager db, Map<Integer, Word> wordCache) {
        this.db = db;
        this.wordCache = wordCache; // Store the map
    }

    public double pEosGivenWord(int id) throws SQLException {
        Word word = wordCache.get(id);
        // Add a null check just in case
        if (word == null) {
            // This case should be rare since FeatureBuilder skips unknown words
            return 0.5; // neutral fallback
        }
        return (word.getEndSequenceCount() + 1.0) / (word.getTotalOccurrences() + 2.0);
    }

    public double pEosGivenContext(Integer w1, Integer w2, Integer w3) throws SQLException {
        // Backoff hierarchy: trigram → bigram → unigram
        if (w1 != null && w2 != null && w3 != null) {
            double tri = db.getTrigramEndProbability(w1, w2, w3);
            if (tri > 0.0) return tri;
        }

        if (w2 != null && w3 != null) {
            double bi = db.getBigramEndProbability(w2, w3);
            if (bi > 0.0) return bi;
        }

        if (w3 != null) {
            double uni = db.getUnigramEndProbability(w3);
            if (uni > 0.0) return uni;
        }
        return 0.5; // neutral fallback (uninformative)
    }


    public double pEosGivenLength(int length) {
        // empirical P(EOS | length) precomputed or approximate decay
        return 1.0 / (1.0 + Math.exp(-(length - 10) / 5.0)); // temp sigmoid
    }
}
