/**
 * EosPredictor.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Vincent Phan
 * Date: November 15, 2025
 *
 * Description: Implements the final prediction logic for the End-of-Sentence (EOS)
 * detector.
 *
 *
 * This class coordinates several components to produce a single,
 * calibrated probability (0.0 to 1.0) that a given input sentence
 * is complete.
 *
 * The prediction pipeline is as follows:
 * 1.  The input sentence is tokenized.
 * 2.  The last three tokens (words) are extracted.
 * 3.  The `ProbabilityEstimator` is used to calculate three
 * separate probabilities (features):
 * a) P(EOS | Context): Probability of EOS given the last 3 words.
 * b) P(EOS | Word): Probability of EOS given just the last word.
 * c) P(EOS | Length): Probability of EOS given the current
 * sentence length (in tokens).
 * 4.  These three probabilities are converted to log-odds (logit)
 * to be used as input features for the logistic regression model.
 * 5.  The pre-trained `LogisticRegressionEOS` model evaluates these
 * three features to produce the final, combined probability.
 *
 * This class ties together the probability model from
 * `ProbabilityEstimator` with the machine learning model
 * `LogisticRegressionEOS` to make a final decision.
 */


package org.utd.cs.sentencebuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class EosPredictor {

    private static final Logger logger = LoggerFactory.getLogger(EosPredictor.class);

    private final LogisticRegressionEOS model;
    private final ProbabilityEstimator probEstimator;
    private final Map<String, Integer> wordToId;


    public EosPredictor(LogisticRegressionEOS model,
                        ProbabilityEstimator probEstimator,
                        Map<String, Integer> wordToId) {
        this.model = model;
        this.probEstimator = probEstimator;
        this.wordToId = wordToId;
    }

    public double predictEosProbability(String sentenceText) {
        if (sentenceText == null || sentenceText.isBlank()) {
            return 0.0;
        }

        List<String> tokens = Tokenizer.tokenizeSentence(sentenceText);
        if (tokens.isEmpty()) {
            return 0.0;
        }

        int len = tokens.size();

        String t3_str = tokens.get(len - 1);
        String t2_str = (len > 1) ? tokens.get(len - 2) : null;
        String t1_str = (len > 2) ? tokens.get(len - 3) : null;

        Integer w3 = wordToId.get(t3_str);
        Integer w2 = (t2_str != null) ? wordToId.get(t2_str) : null;
        Integer w1 = (t1_str != null) ? wordToId.get(t1_str) : null;

        if (w3 == null) {
            // Last word is Out-of-Vocabulary.
            logger.warn("Last word '{}' is OOV. Returning 0.5.", t3_str);
            //fallback
            return 0.5;
        }

        // 1. Calculate the 3 probabilities (x1, x2, x3)
        double pEosContext = probEstimator.pEosGivenContext(w1, w2, w3);
        double pEosWord = probEstimator.pEosGivenWord(w3);
        double pEosLength = probEstimator.pEosGivenLength(len);

        // 2. Convert to log-odds (logit)
        double x1 = safeLogit(pEosContext);
        double x2 = safeLogit(pEosWord);
        double x3 = safeLogit(pEosLength);

        // 3. Evaluate using the logistic regression model
        return model.eval(x1, x2, x3);
    }

    /**
     * Safely converts a probability (0-1) to log-odds (logit).
     * Clips input to avoid log(0) or division by zero.
     */
    private static double safeLogit(double p) {
        double eps = 1e-9;
        p = Math.min(Math.max(p, eps), 1 - eps);
        return Math.log(p / (1 - p));
    }
}
