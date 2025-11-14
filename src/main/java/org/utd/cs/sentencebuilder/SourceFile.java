package org.utd.cs.sentencebuilder;
import java.sql.Timestamp;

public record SourceFile(Integer fileId, String fileName, int wordCount, Timestamp importTimestamp) {
    public SourceFile(String fileName, int wordCount) {
        this(null, fileName, wordCount, null);
    }
}