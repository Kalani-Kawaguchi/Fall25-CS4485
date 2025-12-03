package org.utd.cs.sentencebuilder;

public interface ProgressCallback {
    // progress is 0.0 to 1.0
    void update(double progress, String message);
}