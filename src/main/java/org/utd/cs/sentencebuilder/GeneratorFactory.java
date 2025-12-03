/**
 *  Author: Kevin Tran
 *  CS 4485 Fall 2025
 *  November 14 2025
 *
 *  Description:
 *  Factory class responsible for constructing the correct sentence
 *  generation strategy based on the requested algorithm name.
 *
 *  Supported algorithms:
 *    - "bi-greedy"   → BigramGreedyGenerator
 *    - "bi-weighted" → BigramWeightedGenerator
 *    - "tri-greedy"   → TrigramGreedyGenerator
 *    - "tri-weighted" → TrigramWeightedGenerator
 *
 *  This allows the UI, CLI, and any other component to request a generator
 *  without needing to understand how each class is constructed.
 */

package org.utd.cs.sentencebuilder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class GeneratorFactory {

    public static SentenceGenerator create(String algo, GeneratorDataController data) throws IOException {
        System.out.println(algo);
        ProbabilityEstimator estimator = new ProbabilityEstimator(
                data.getUnigramStats(),
                data.getBigramStats(),
                data.getTrigramStats(),
                data.getLengthProbs()
        );
        File modelLoadPath = new File("data/model/model.json");

        LogisticRegressionEOS model = LogisticRegressionEOS.loadModel(modelLoadPath);
        System.out.println(model.getB() + " " + model.getW1() + " " + model.getW2() + " " + model.getW3());
        EosPredictor predictor = new EosPredictor(model, estimator);

        Map<String, Integer> w2i = data.getWordToId();
        Map<Integer, String> i2w = data.getIdToWord();

        switch (algo) {
            case "bi_weighted":
                System.out.println("bi weighted created");
                return new BigramWeightedGenerator(
                        w2i,
                        i2w,
                        data.getBigramFollowers(),
                        data.getStartCandidates()
                );
            case "bi_greedy":
                System.out.println("bi greedy created");
                return new BigramGreedyGenerator(
                        w2i,
                        i2w,
                        data.getBigramFollowers(),
                        data.getStartCandidates()
                );

            case "bi_greedy_eos":
                return new BigramGreedyUsingEOSPredictor(
                        data,
                        predictor
                );

            case "tri_weighted":
                return new TrigramWeightedGenerator(
                        w2i,
                        i2w,
                        data.getTrigramFollowers(),
                        data.getStartCandidates()
                );

            case "tri_greedy":
                return new TrigramGreedyGenerator(
                        w2i,
                        i2w,
                        data.getTrigramFollowers(),
                        data.getStartCandidates()
                );

            default:
                return null;
        }
    }
}
