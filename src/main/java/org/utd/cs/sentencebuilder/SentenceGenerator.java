package org.utd.cs.sentencebuilder;

import java.util.List;

public interface SentenceGenerator {

    /**
     * Human-readable name for the generator
     * e.g., "Greedy Bigram", "Weighted Random", "Beam Search"
     */
    String getName();

    /**
     * Load any required data into memory from the DB.
     * Should be called once before generating sentences.
     */
    void loadData();

    /**
     * Generate a sentence from scratch.
     */
    String generateSentence();

    /**
     * Generate a sentence starting with one or more words.
     * Usually used when the user gives a starting word in the UI.
     */
    String generateSentence(List<String> startingWords);
}
