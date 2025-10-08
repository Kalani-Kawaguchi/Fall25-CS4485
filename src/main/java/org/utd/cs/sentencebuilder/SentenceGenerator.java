package org.utd.cs.sentencebuilder;
import java.util.List;

public interface SentenceGenerator {
    /**
     * Generate a random sentence.
     */
    String generateSentence();

    /**
     * Generate a sentence starting with one or more given words.
     */
    String generateSentence(List<String> startingWords);

    /**
     * Load all data structures into memory.
     */
    void loadData();


    String getName();
}
