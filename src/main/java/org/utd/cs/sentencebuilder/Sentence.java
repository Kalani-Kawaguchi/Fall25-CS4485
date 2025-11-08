package org.utd.cs.sentencebuilder;


import java.util.Objects;

public class Sentence {

    private Integer sentenceId;
    private String text;
    private int tokenCount;
    private int sentenceOccurrences;

    // Default constructor
    public Sentence() { }

    /**
     * Constructor for creating a new sentence from the tokenizer.
     * Initializes occurrences to 0, ready for counting.
     */
    public Sentence(String text, int tokenCount) {
        this.text = text;
        this.tokenCount = tokenCount;
        this.sentenceOccurrences = 0;
    }

    /**
     * Constructor for hydrating a full Sentence object from the database.
     */
    public Sentence(Integer sentenceId, String text, int tokenCount, int sentenceOccurrences) {
        this.sentenceId = sentenceId;
        this.text = text;
        this.tokenCount = tokenCount;
        this.sentenceOccurrences = sentenceOccurrences;
    }

    // --- Getters and Setters ---

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public int getSentenceOccurrences() {
        return sentenceOccurrences;
    }

    public void setSentenceOccurrences(int sentenceOccurrences) {
        this.sentenceOccurrences = sentenceOccurrences;
    }

    public Integer getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(Integer sentenceId) {
        this.sentenceId = sentenceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sentence sentence = (Sentence) o;
        return Objects.equals(text, sentence.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}