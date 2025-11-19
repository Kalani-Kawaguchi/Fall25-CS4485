/**
 *  Author: Kevin Tran
 *  CS 4485 Fall 2025
 *  November 14 2025
 *
 *  Description:
 *  Simple factory for selecting a SentenceGenerator implementation
 *  based on a string key (e.g., "greedy", "weighted").
 *
 *  This lets the rest of the code (CLI, JavaFX UI, tests) choose
 *  different generation strategies without hard-coding class names.
 *
 *  Currently supported:
 *    - "greedy"   -> BigramGreedyGenerator
 *    - "weighted" -> BigramWeightedGenerator
 *
 *  Any unknown or null algo name will default to the greedy generator.
 */

package org.utd.cs.sentencebuilder;

public class GeneratorFactory {

    private GeneratorFactory() {
        // utility class; no instances
    }

    public static SentenceGenerator create(String algo, GeneratorDataController data) {
        String mode = (algo == null ? "greedy" : algo.toLowerCase());

        switch (mode) {
            case "weighted":
                return new BigramWeightedGenerator(
                        data.getWordToId(),
                        data.getIdToWord(),
                        data.getFollowers(),
                        data.getStartCandidates()
                );
            case "greedy":
            default:
                return new BigramGreedyGenerator(
                        data.getWordToId(),
                        data.getIdToWord(),
                        data.getFollowers(),
                        data.getStartCandidates()
                );
        }
    }
}
