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


    public EosPredictor(LogisticRegressionEOS model,
                        ProbabilityEstimator probEstimator) {
        this.model = model;
        this.probEstimator = probEstimator;
    }

    /**
     * Calculates the probability that the current sequence of tokens ends a sentence.
     * * @param currentSentenceIds The growing list of token IDs generated so far.
     * @return double Probability (0.0 - 1.0)
     */
    public double predictEosProbability(List<Integer> currentSentenceIds) {
        if (currentSentenceIds == null || currentSentenceIds.isEmpty()) {
            return 0.0;
        }

        int len = currentSentenceIds.size();

        Integer w3 = currentSentenceIds.get(len - 1); // The last word (candidate for end)
        Integer w2 = (len > 1) ? currentSentenceIds.get(len - 2) : null;
        Integer w1 = (len > 2) ? currentSentenceIds.get(len - 3) : null;

        // 1. Calculate the 3 probabilities (x1, x2, x3) using the Estimator
        double pEosContext = probEstimator.pEosGivenContext(w1, w2, w3);
        double pEosWord    = probEstimator.pEosGivenWord(w3);
        double pEosLength  = probEstimator.pEosGivenLength(len);

        // 2. Convert to log-odds (logit)
        double x1 = safeLogit(pEosContext);
        double x2 = safeLogit(pEosWord);
        double x3 = safeLogit(pEosLength);

        //logger.info("{} {} {}", x1, x2, x3);
        // 3. Evaluate using the logistic regression model
        double probability = model.eval(x1, x2, x3);

        logger.debug("EOS Pred: len={} [{},{},{}] -> prob={}", len, w1, w2, w3, probability);

        return probability;
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
