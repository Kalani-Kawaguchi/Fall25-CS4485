package org.utd.cs.sentencebuilder;

import java.sql.SQLException;
import java.util.*;

public class Main {
    public static void main(String[] args) {

        // ai generated test cases
        DatabaseManager db = new DatabaseManager();

        try {
            db.buildDatabase();

            // Prepare word collection for batch insert
            List<Word> words = new ArrayList<>();

            Word w1 = new Word("hello");
            w1.setTotalOccurrences(3);
            w1.setStartSentenceCount(1);
            w1.setEndSequenceCount(0);

            Word w2 = new Word("world");
            w2.setTotalOccurrences(2);
            w2.setStartSentenceCount(0);
            w2.setEndSequenceCount(1);

            Word w3 = new Word("naïve"); // Unicode test
            w3.setTotalOccurrences(1);
            w3.setStartSentenceCount(0);
            w3.setEndSequenceCount(0);

            Word w4 = new Word(""); // Empty string test
            w4.setTotalOccurrences(1);
            w4.setStartSentenceCount(0);
            w4.setEndSequenceCount(0);

            Word w5 = new Word("hello"); // Duplicate entry test
            w5.setTotalOccurrences(5);
            w5.setStartSentenceCount(0);
            w5.setEndSequenceCount(2);

            words.add(w1);
            words.add(w2);
            words.add(w3);
            words.add(w4);
            words.add(w5);

            System.out.println("\n=== Testing addWordsInBatch() ===");
            db.addWordsInBatch(words);
            System.out.println("Words inserted/updated successfully.");

            // Fetch single word ID
            System.out.println("\n=== Testing getWordId() ===");
            int helloId = db.getWordId("hello");
            int worldId = db.getWordId("world");
            int missingId = db.getWordId("missing");

            System.out.println("ID for 'hello': " + helloId);
            System.out.println("ID for 'world': " + worldId);
            System.out.println("ID for 'missing' (should be -1): " + missingId);

            // Fetch multiple IDs
            System.out.println("\n=== Testing getWordIds() ===");
            List<Word> lookupWords = Arrays.asList(
                    new Word("hello"),
                    new Word("world"),
                    new Word("naïve"),
                    new Word("missing")
            );

            Map<String, Integer> wordIds = db.getWordIds(lookupWords);

            for (Map.Entry<String, Integer> entry : wordIds.entrySet()) {
                System.out.printf("Word '%s' -> ID %d%n", entry.getKey(), entry.getValue());
            }

            // Check for empty or null inputs
            System.out.println("\n=== Testing getWordIds() with empty list ===");
            Map<String, Integer> emptyResult = db.getWordIds(Collections.emptyList());
            System.out.println("Result for empty list: " + emptyResult);

            System.out.println("\n=== Testing getWordIds() with null ===");
            Map<String, Integer> nullResult = db.getWordIds(null);
            System.out.println("Result for null input: " + nullResult);

            // Check for retrieval of Word objects
            System.out.println("\n=== Fetching full Word objects from DB ===");
            Word helloWord = db.getWord("hello");
            Word unicodeWord = db.getWord("naïve");
            Word emptyWord = db.getWord("");

            System.out.println(helloWord);
            System.out.println(unicodeWord);
            System.out.println(emptyWord);

            DatabaseManager.closeDataSource();

        } catch (SQLException e) {
            System.err.println("SQL error during testing:");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error:");
            e.printStackTrace();
        }

    }
}