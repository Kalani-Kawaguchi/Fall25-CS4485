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
    private Map<String, Integer> wordToId = new HashMap<>();
    private Map<Integer, Word> idToWord = new HashMap<>();
    private List<SentenceFeature> featureBatch = new ArrayList<>();

    public FeatureBuilder(DatabaseManager db) throws SQLException {
        this.db = db;
        loadWordMappings();

        this.probEstimator = new ProbabilityEstimator(db, this.idToWord);
    }

    public void loadWordMappings() throws SQLException {
        logger.info("Loading word-to-ID mappings...");
        idToWord = db.getAllWords();
        wordToId = db.getWordIds();
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

                // --- Resolve word IDs safely ---
                Integer id = wordToId.get(unigram);
                if (id == null) {
                    logger.warn("Unknown word '{}' encountered. Skipping feature generation for it.", token);
                    continue;
                }

                Integer w3 = wordToId.get(tokens.get(i));             // current
                Integer w2 = (i > 0) ? wordToId.get(tokens.get(i - 1)) : null;
                Integer w1 = (i > 1) ? wordToId.get(tokens.get(i - 2)) : null;

                // --- Compute probabilities ---
                double pEosWord = probEstimator.pEosGivenWord(id);
                double pEosContext = probEstimator.pEosGivenContext(w1, w2, w3);
                double pEosLength = probEstimator.pEosGivenLength(len);

                // --- Convert to logits ---
                double x1 = safeLogit(pEosContext);
                double x2 = safeLogit(pEosWord);
                double x3 = safeLogit(pEosLength);
                // --- Build feature object ---
                SentenceFeature feature = new SentenceFeature();
                feature.setSentenceId(sentence.getSentenceId());
                feature.setTokenIndex(i);
                feature.setWord(token);
                feature.setContextType(getContextType(i));
                feature.setContextNgram(trigram);
                feature.setSentenceLen(len);
                feature.setpEosContext(pEosContext);
                feature.setpEosWord(pEosWord);
                feature.setpEosLength(pEosLength);
                feature.setX1(x1);
                feature.setX2(x2);
                feature.setX3(x3);
                feature.setLabel(isLast ? 1 : 0);

                featureBatch.add(feature);

                // Batch flush
                if (featureBatch.size() >= BATCH_SIZE) {
                    db.bulkAddSentenceFeatures(featureBatch);
                    featureBatch.clear();
                }
            }
        } catch (Exception e) {
            logger.error("Error processing sentence ID {}: {}", sentence.getSentenceId(), e.getMessage(), e);
        }
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
            db.bulkAddSentenceFeatures(featureBatch);
            featureBatch.clear();
        }
        logger.info("Feature generation complete.");
    }

}


