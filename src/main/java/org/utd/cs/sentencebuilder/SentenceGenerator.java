package org.utd.cs.sentencebuilder;

import java.util.List;

public interface SentenceGenerator {
    /**
     * Generate a random sentence with the generator's default settings.
     */
    String generateSentence();

    /**
     * Generate a sentence starting with one or more given words,
     * using the generator's default settings.
     */
    String generateSentence(List<String> startingWords);

    /**
     * Load all data structures into memory.
     */
    void loadData();

    /**
     * Human-readable name of this generator (for CLI/UI).
     */
    String getName();

    /**
     * Extended API: generate with a max token cap and an optional stop word.
     * Default implementation ignores max/stop and just calls the basic version.
     */
    default String generateSentence(int maxTokens, String stopWord) {
        return generateSentence();
    }

    /**
     * Extended API: starting words + max length + optional stop word.
     * Default implementation ignores max/stop and just calls the basic version.
     */
    default String generateSentence(List<String> startingWords, int maxTokens, String stopWord) {
        return generateSentence(startingWords);
    }
}
