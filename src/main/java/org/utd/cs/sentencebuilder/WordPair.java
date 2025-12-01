package org.utd.cs.sentencebuilder;

/**
 * Represents a sequence of two words in the 'word_pairs' table.
 * This class is a simple Plain Old Java Object (POJO) to hold word pair data.
 */
public class WordPair {
    private int sequenceId;
    private int precedingWordId;
    private int followingWordId;
    private int occurrenceCount;
    private int biEndFrequency;

    // Constructors
    public WordPair() {}

    public WordPair(int precedingWordId, int followingWordId) {
        this.precedingWordId = precedingWordId;
        this.followingWordId = followingWordId;
    }

    // Getters and Setters
    public int getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(int sequenceId) {
        this.sequenceId = sequenceId;
    }

    public int getPrecedingWordId() {
        return precedingWordId;
    }

    public void setPrecedingWordId(int precedingWordId) {
        this.precedingWordId = precedingWordId;
    }

    public int getFollowingWordId() {
        return followingWordId;
    }

    public void setFollowingWordId(int followingWordId) {
        this.followingWordId = followingWordId;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public int getEndFrequency() {return biEndFrequency; }

    public void setEndFrequency(int biEndFrequency) {this.biEndFrequency = biEndFrequency; }


    @Override
    public String toString() {
        return "WordPair{" +
                "sequenceId=" + sequenceId +
                ", precedingWordId=" + precedingWordId +
                ", followingWordId=" + followingWordId +
                ", occurrenceCount=" + occurrenceCount +
                '}';
    }
}
