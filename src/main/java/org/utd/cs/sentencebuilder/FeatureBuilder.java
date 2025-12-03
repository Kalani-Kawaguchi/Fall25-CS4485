/**
 * FeatureBuilder.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Vincent Phan
 * Date: November 6, 2025
 *
 * Description: Feature extraction and training data generation
 *
 * This class takes raw text and iterates through the sentence corpus
 * to construct the labeled dataset (X, Y) required for training the
 * Logistic Regression classifier
 */

package org.utd.cs.sentencebuilder;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FeatureBuilder {
    private static final Logger logger = LoggerFactory.getLogger(FeatureBuilder.class);
    private static final int BATCH_SIZE = 100000;
    // some victorian text have unreasonably long run-on sentences that are poisoning our data.
    private static final int MAX_REAL_TRAINING_LENGTH = 60;
    private static final int SYNTHETIC_BUFFER = 200;
    private static final int SAMPLES_PER_LENGTH_STEP = 200;

    private final DatabaseManager db;
    private final ProbabilityEstimator probEstimator;

    // Primitive maps for the ProbabilityEstimator
    // [0] = total occurrences, [1] = end sequence count
    private final Map<Integer, int[]> unigramStats = new HashMap<>();
    // Key: (w1 << 32 | w2) -> Value: [total, endCount]
    private final Map<Long, int[]> bigramStats = new HashMap<>();
    // Key: (w1 << 32 | w2) -> Value: Map<w3, [total, endCount]>
    private final Map<Long, Map<Integer, int[]>> trigramStats = new HashMap<>();
    private Map<String, Integer> wordToId = new HashMap<>();
    private Map<Integer, Double> lengthHistogram = new HashMap<>();
    private List<SentenceFeature> featureBatch = new ArrayList<>();

    public FeatureBuilder(DatabaseManager db) throws SQLException {
        this.db = db;
        loadWordMappings();

        logger.info("Structuring complete. Forcing GC to clear raw object maps...");
        System.gc();

        this.probEstimator = new ProbabilityEstimator(unigramStats, bigramStats, trigramStats, lengthHistogram);
    }

    public void loadWordMappings() throws SQLException {
        logger.info("Loading word-to-ID mappings...");
        wordToId = db.getWordIds();
        lengthHistogram = db.getLengthProbabilityMap();
        Map<Integer, Word> rawWords = db.getAllWords();
        Map<Integer, Map<Integer, WordPair>> rawBigrams = db.getAllWordPairs();
        Map<Integer, Map<Integer, Map<Integer, WordTriplet>>> rawTrigrams = db.getAllWordTriplets();

        // 2. Flatten Unigrams: Word -> int[]
        unigramStats.clear();
        for (Word w : rawWords.values()) {
            unigramStats.put(w.getWordId(), new int[]{ w.getTotalOccurrences(), w.getEndSequenceCount() });
        }

        // 3. Flatten Bigrams: Map<w1, Map<w2, WP>> -> Map<Long, int[]>
        bigramStats.clear();
        for (Map.Entry<Integer, Map<Integer, WordPair>> outer : rawBigrams.entrySet()) {
            int w1 = outer.getKey();
            for (Map.Entry<Integer, WordPair> inner : outer.getValue().entrySet()) {
                int w2 = inner.getKey();
                WordPair wp = inner.getValue();

                long key = pack(w1, w2);
                bigramStats.put(key, new int[]{ wp.getOccurrenceCount(), wp.getEndFrequency() });
            }
        }

        trigramStats.clear();
        for (Map.Entry<Integer, Map<Integer, Map<Integer, WordTriplet>>> level1 : rawTrigrams.entrySet()) {
            int w1 = level1.getKey();
            for (Map.Entry<Integer, Map<Integer, WordTriplet>> level2 : level1.getValue().entrySet()) {
                int w2 = level2.getKey();
                long key = pack(w1, w2);

                trigramStats.putIfAbsent(key, new HashMap<>());
                Map<Integer, int[]> innerStats = trigramStats.get(key);

                for (Map.Entry<Integer, WordTriplet> level3 : level2.getValue().entrySet()) {
                    int w3 = level3.getKey();
                    WordTriplet wt = level3.getValue();
                    innerStats.put(w3, new int[]{ wt.getOccurrenceCount(), wt.getEndFrequency() });
                }
            }
        }

        logger.info("Loaded {} unigrams, {} bigrams, and {} trigram contexts.",
                unigramStats.size(), bigramStats.size(), trigramStats.size());
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

            if (len > MAX_REAL_TRAINING_LENGTH) {
                return;
            }

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
    // Features are too collinear, Thus we will augment our feature space
    // by injecting synthetic feature vectors that decorrelate N-Gram scores from
    // length scores to force the model to learn the weight for the length feature.
    public void generateSyntheticFeatures() throws SQLException {
        logger.info("Injecting synthetic feature-space vectors to fix Length Weight...");

        int startLen = MAX_REAL_TRAINING_LENGTH + 1;
        int syntheticLimit = startLen + SYNTHETIC_BUFFER;

        logger.info("Detected max real length: {}. Generating synthetic tail up to {}.", startLen, syntheticLimit);
        Random rng = new Random();
        int synthIdCounter = -1;
        // We will generate data points from the end of the corpus up to a high limit
        for (int L = startLen + 1; L <= syntheticLimit; L++) {
            double baseProb = syntheticLengthProbability(L, startLen, syntheticLimit);
            double baseLogit = safeLogit(baseProb);

            for (int k = 0; k < SAMPLES_PER_LENGTH_STEP; k++) {
                // prevent exact duplicates
                double jitter = (rng.nextDouble() * 0.1) - 0.05;
                // Positive EOS
                SentenceFeature fPos = new SentenceFeature();
                fPos.setSentenceId(synthIdCounter--);
                fPos.setTokenIndex(L - 1);
                fPos.setWord("<SYNTH_POS>");
                fPos.setContextType("synthetic");
                fPos.setContextNgram("synth_pos");
                fPos.setSentenceLen(L);
                fPos.setpEosLength(baseProb);

                // X1, X2 = 0.0 (Neutral/Unknown)
                fPos.setX1(0.0);
                fPos.setX2(0.0);
                fPos.setX3(baseLogit + jitter);

                fPos.setLabel(1);
                featureBatch.add(fPos);

                double lowProb = 0.01;
                double lowLogit = safeLogit(lowProb);

                // Negative EOS
                SentenceFeature fNeg = new SentenceFeature();
                fNeg.setSentenceId(synthIdCounter--);
                fNeg.setTokenIndex(L - 1);
                fNeg.setWord("<SYNTH_NEG>");
                fNeg.setContextType("synthetic");
                fNeg.setContextNgram("synth_neg");
                fNeg.setSentenceLen(L);
                fNeg.setpEosLength(lowProb);

                fNeg.setX1(0.0);
                fNeg.setX2(0.0);
                fNeg.setX3(lowLogit + jitter); // Low Negative Logit (e.g., -4.5)

                fNeg.setLabel(0);
                featureBatch.add(fNeg);

                if (featureBatch.size() >= BATCH_SIZE) flushBatch();
            }
        }

        flushBatch();
    }

    /**
     * Compute synthetic length probability for unseen long sentences.
     */
    private double syntheticLengthProbability(int length, int maxCorpusLen, int syntheticLimit) {
        // We want prob to go from ~0.1 to 0.99 as length goes from maxCorpus to 200
        double maxProb = 0.99; // Cap at 99%, not 50%

        // Simple linear interpolation
        double progress = (double)(length - maxCorpusLen) / (syntheticLimit - maxCorpusLen);
        double prob = 0.1 + (progress * (maxProb - 0.1));

        return Math.min(0.99, Math.max(0.01, prob));
    }

    private void flushBatch() throws SQLException {
        if (featureBatch.isEmpty()) return;
        try {
            db.addSentenceFeaturesInBatch(featureBatch);
        } catch (SQLException e) {
            logger.error("Error flushing batch: {}", e.getMessage(), e);
        }
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
    public void buildAllSentenceFeatures(ProgressCallback callback) throws SQLException {
        logger.info("Starting sentence feature generation...");
        // Ensure batch is clear before starting
        this.featureBatch.clear();

        long totalSentences = db.getSentenceCount();
        AtomicInteger processedCount = new AtomicInteger(0);

        db.processSentences(sentence -> {

            // A. Run the heavy logic
            processSentence(sentence);

            // B. Increment and Update UI
            int current = processedCount.incrementAndGet();

            // Only update UI every 500 records to prevent freezing
            if (callback != null && current % 500 == 0) {
                double progress = (double) current / totalSentences;
                // Cap at 95% so we leave room for the Synthetic step
                double scaledProgress = progress * 0.95;
                callback.update(scaledProgress,
                        String.format("Extracting Features: %d / %d", current, totalSentences));
            }
        });

        // After all sentences are processed, flush any remaining features
        if (!featureBatch.isEmpty()) {
            logger.info("Flushing final batch of {} features.", featureBatch.size());
            flushBatch();
        }

        logger.info("Generating synthetic EOS samples");
        generateSyntheticFeatures();

        if (callback != null) callback.update(1.0, "Feature Extraction Complete.");
        logger.info("Feature generation complete.");
    }


    private long pack(int w1, int w2) {
        return ((long) w1 << 32) | (w2 & 0xFFFFFFFFL);
    }

}


