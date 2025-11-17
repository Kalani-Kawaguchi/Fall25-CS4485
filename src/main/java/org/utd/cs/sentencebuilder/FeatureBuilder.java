package org.utd.cs.sentencebuilder;


import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FeatureBuilder {
    private static final Logger logger = LoggerFactory.getLogger(FeatureBuilder.class);
    private static final int BATCH_SIZE = 100000;

    private final DatabaseManager db;
    private final ProbabilityEstimator probEstimator;
    private Map<Integer, Word> idToWord = new HashMap<>();
    private Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> idToTrigram = new HashMap<>();
    private Map<Integer, Map<Integer, WordPair>> idToBigram = new HashMap<>();
    private Map<String, Integer> wordToId = new HashMap<>();
    private Map<Integer, Double> lengthHistogram = new HashMap<>();
    private List<SentenceFeature> featureBatch = new ArrayList<>();

    public FeatureBuilder(DatabaseManager db) throws SQLException {
        this.db = db;
        loadWordMappings();
        this.probEstimator = new ProbabilityEstimator(idToWord, idToBigram, idToTrigram, lengthHistogram);
    }

    public void loadWordMappings() throws SQLException {
        logger.info("Loading word-to-ID mappings...");
        idToWord = db.getAllWords();
        idToBigram = db.getAllWordPairs();
        idToTrigram = db.getAllWordTriplets();
        wordToId = db.getWordIds();
        lengthHistogram = db.getLengthProbabilityMap();
        logger.info("Loaded {} words into cache.", wordToId.size());
    }


    /**
     * Processes a single sentence to compute its EOS prediction features.
     * This method is designed to be passed as a Consumer<Sentence>
     * to DatabaseManager.processSentences().
     *
     * @param sentence The sentence to process.
     */
    public void processSentence(Sentence sentence) {
        try {
            List<String> tokens = Tokenizer.tokenizeSentence(sentence.getText());
            int len = sentence.getTokenCount();
            if (tokens.isEmpty()) return;

            for (int i = 0; i < len; i++) {
                String token = tokens.get(i);
                boolean isLast = (i == len - 1);

                // --- Build context windows ---
                String unigram = token;
                String bigram = (i > 0) ? tokens.get(i - 1) + " " + token : token;
                String trigram = (i > 1) ? tokens.get(i - 2) + " " + tokens.get(i - 1) + " " + token : bigram;

                Integer w3 = wordToId.get(tokens.get(i));
                Integer w2 = (i > 0) ? wordToId.get(tokens.get(i - 1)) : null;
                Integer w1 = (i > 1) ? wordToId.get(tokens.get(i - 2)) : null;

                if (w3 == null) {
                    logger.warn("Unknown word '{}' in sentence {}. Skipping.", token, sentence.getSentenceId());
                    continue;
                }

                // Probability calculations
                double pEosWord = probEstimator.pEosGivenWord(w3);
                double pEosContext = probEstimator.pEosGivenContext(w1, w2, w3);
                double pEosLength = probEstimator.pEosGivenLength(len);

                // Features (logits)
                double x1 = safeLogit(pEosContext);
                double x2 = safeLogit(pEosWord);
                double x3 = safeLogit(pEosLength);

                SentenceFeature feat = new SentenceFeature();
                feat.setSentenceId(sentence.getSentenceId());
                feat.setTokenIndex(i);
                feat.setWord(token);
                feat.setContextType(getContextType(i));
                feat.setContextNgram(trigram);
                feat.setSentenceLen(len);
                feat.setpEosContext(pEosContext);
                feat.setpEosWord(pEosWord);
                feat.setpEosLength(pEosLength);
                feat.setX1(x1);
                feat.setX2(x2);
                feat.setX3(x3);
                feat.setLabel(isLast ? 1 : 0);

                featureBatch.add(feat);
                if (featureBatch.size() >= BATCH_SIZE) flushBatch();
            }
        } catch (Exception e) {
            logger.error("Error processing sentence ID {}: {}", sentence.getSentenceId(), e.getMessage(), e);
        }
    }

    private void flushBatch() throws SQLException {
        if (featureBatch.isEmpty()) return;
        db.addSentenceFeaturesInBatch(featureBatch);
        featureBatch.clear();
    }

    private static double safeLogit(double p) {
        double eps = 1e-9;
        p = Math.min(Math.max(p, eps), 1 - eps);
        return Math.log(p / (1 - p));
    }

    private String getContextType(int i) {
        if (i < 1) return "unigram";
        if (i < 2) return "bigram";
        return "trigram";
    }

    /**
     * Streams all sentences from the database and computes features for each.
     * This will safely process large datasets without excessive memory use.
     */
    public void buildAllSentenceFeatures() throws SQLException {
        logger.info("Starting sentence feature generation...");
        // Ensure batch is clear before starting
        this.featureBatch.clear();

        db.processSentences(this::processSentence);

        // After all sentences are processed, flush any remaining features
        if (!featureBatch.isEmpty()) {
            logger.info("Flushing final batch of {} features.", featureBatch.size());
            db.addSentenceFeaturesInBatch(featureBatch);
            featureBatch.clear();
        }
        logger.info("Feature generation complete.");
    }

}


