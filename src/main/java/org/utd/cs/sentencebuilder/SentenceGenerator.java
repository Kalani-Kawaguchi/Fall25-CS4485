package org.utd.cs.sentencebuilder;

import java.util.List;

/**
 * Contract for all sentence generator strategies.
 *
 * Implementations can optionally support maxTokens/stopWord via the
 * extended default methods.
 */
public interface SentenceGenerator {

    String getName();

    // Basic versions
    String generateSentence();
    String generateSentence(List<String> startingWords);

    // Extended versions with options; default to the simple ones
    default String generateSentence(int maxTokens, String stopWord) {
        return generateSentence();
    }

    default String generateSentence(List<String> startingWords, int maxTokens, String stopWord) {
        return generateSentence(startingWords);
    }
}
