package org.github.vincentphan1;

/**
 * Represents a single word entry in the 'words' table.
 * This class is a simple Plain Old Java Object (POJO) to hold word data.
 */
public class Word {

    private int wordId;
    private String wordValue; // The actual word string
    private int totalOccurrences;
    private int startSentenceCount;
    private int endSequenceCount;

    // Constructors
    public Word() {}

    public Word(String wordValue) {
        this.wordValue = wordValue;
    }

    // Getters and Setters
    public int getWordId() {
        return wordId;
    }

    public void setWordId(int wordId) {
        this.wordId = wordId;
    }

    public String getWordValue() {
        return wordValue;
    }

    public void setWordValue(String wordValue) {
        this.wordValue = wordValue;
    }

    public int getTotalOccurrences() {
        return totalOccurrences;
    }

    public void setTotalOccurrences(int totalOccurrences) {
        this.totalOccurrences = totalOccurrences;
    }

    public int getStartSentenceCount() {
        return startSentenceCount;
    }

    public void setStartSentenceCount(int startSentenceCount) {
        this.startSentenceCount = startSentenceCount;
    }

    public int getEndSequenceCount() {
        return endSequenceCount;
    }

    public void setEndSequenceCount(int endSequenceCount) {
        this.endSequenceCount = endSequenceCount;
    }

    @Override
    public String toString() {
        return "Word{" +
                "wordId=" + wordId +
                ", wordValue='" + wordValue + '\'' +
                ", totalOccurrences=" + totalOccurrences +
                ", startSentenceCount=" + startSentenceCount +
                ", endSequenceCount=" + endSequenceCount +
                '}';
    }
}
