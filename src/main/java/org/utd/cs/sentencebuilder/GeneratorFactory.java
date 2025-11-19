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

import java.util.List;
import java.util.Map;

public class GeneratorFactory {

    public static SentenceGenerator create(String algo, GeneratorDataController data) {

        Map<String, Integer> w2i = data.getWordToId();
        Map<Integer, String> i2w = data.getIdToWord();

        switch (algo) {
            case "bi_weighted":
                return new BigramWeightedGenerator(
                        w2i,
                        i2w,
                        data.getBigramFollowers(),
                        data.getStartCandidates()
                );
            case "bi_greedy":
                return new BigramGreedyGenerator(
                        w2i,
                        i2w,
                        data.getBigramFollowers(),
                        data.getStartCandidates()
                );

            case "tri_weighted":
                return new TrigramWeightedGenerator(
                        w2i,
                        i2w,
                        data.getTrigramFollowers(),
                        data.getStartCandidates()
                );

            case "tri_greedy":
            default:
                return new TrigramGreedyGenerator(
                        w2i,
                        i2w,
                        data.getTrigramFollowers(),
                        data.getStartCandidates()
                );
        }
    }
}
