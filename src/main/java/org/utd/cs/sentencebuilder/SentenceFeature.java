package org.utd.cs.sentencebuilder;

/**
 * Represents a feature vector for a token within a sentence,
 * used to model end-of-sentence (EOS) prediction.
 */
public class SentenceFeature {

    private int sentenceId;
    private int tokenIndex;
    private String word;
    private String contextType; // 'unigram', 'bigram', 'trigram'
    private String contextNgram;
    private int sentenceLen;
    private double pEosContext;
    private double pEosWord;
    private double pEosLength;
    private double x1;
    private double x2;
    private double x3;
    private int label; // 1 if EOS follows, else 0

    // --- Getters and Setters ---
    public int getSentenceId() { return sentenceId; }
    public void setSentenceId(int sentenceId) { this.sentenceId = sentenceId; }

    public int getTokenIndex() { return tokenIndex; }
    public void setTokenIndex(int tokenIndex) { this.tokenIndex = tokenIndex; }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public String getContextType() { return contextType; }
    public void setContextType(String contextType) { this.contextType = contextType; }

    public String getContextNgram() { return contextNgram; }
    public void setContextNgram(String contextNgram) { this.contextNgram = contextNgram; }

    public int getSentenceLen() { return sentenceLen; }
    public void setSentenceLen(int sentenceLen) { this.sentenceLen = sentenceLen; }

    public double getpEosContext() { return pEosContext; }
    public void setpEosContext(double pEosContext) { this.pEosContext = pEosContext; }

    public double getpEosWord() { return pEosWord; }
    public void setpEosWord(double pEosWord) { this.pEosWord = pEosWord; }

    public double getpEosLength() { return pEosLength; }
    public void setpEosLength(double pEosLength) { this.pEosLength = pEosLength; }

    public double getX1() { return x1; }
    public void setX1(double x1) { this.x1 = x1; }

    public double getX2() { return x2; }
    public void setX2(double x2) { this.x2 = x2; }

    public double getX3() { return x3; }
    public void setX3(double x3) { this.x3 = x3; }

    public int getLabel() { return label; }
    public void setLabel(int label) { this.label = label; }
}
