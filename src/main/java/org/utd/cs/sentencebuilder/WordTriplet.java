package org.utd.cs.sentencebuilder;

/**
 * Represents a sequence of two words in the 'trigrams' table.
 * This class is a simple Plain Old Java Object (POJO) to hold word pair data.
 */
public class WordTriplet {
    private int sequenceId;
    private int firstWordId;
    private int secondWordId;
    private int thirdWordId;
    private int occurrenceCount;
    private int triEndFrequency;

    // Constructors
    public WordTriplet() {}

    public WordTriplet(int firstWordId, int secondWordId, int thirdWordId, int occurrenceCount) {
        this.firstWordId = firstWordId;
        this.secondWordId = secondWordId;
        this.thirdWordId = thirdWordId;
        this.occurrenceCount = occurrenceCount;
    }

    // Getters and Setters
    public int getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(int sequenceId) {
        this.sequenceId = sequenceId;
    }

    public int getFirstWordId() {
        return firstWordId;
    }

    public void setFirstWordId(int firstWordId) {
        this.firstWordId = firstWordId;
    }

    public int getSecondWordId() {
        return secondWordId;
    }

    public void setSecondWordId(int secondWordId) {this.secondWordId = secondWordId;}

    public int getThirdWordId() {
        return thirdWordId;
    }

    public void setThirdWordId(int thirdWordId) {this.thirdWordId = thirdWordId;}

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public int getOccurenceCount() {return occurrenceCount; }

    public int getEndFrequency() {return triEndFrequency; }

    public void setEndFrequency(int triEndFrequency) {this.triEndFrequency = triEndFrequency; }


}