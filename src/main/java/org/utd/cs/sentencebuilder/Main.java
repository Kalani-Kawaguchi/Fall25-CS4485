package org.utd.cs.sentencebuilder;

import java.sql.SQLException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        DatabaseManager db = new DatabaseManager();

        try {
            System.out.println("=== Sentence Builder Database Test ===");

            // 1️⃣ Build schema (creates tables if they don't exist)
            System.out.println("Building database schema...");
            db.buildDatabase();

            // 2️⃣ Clear existing data (optional for clean test runs)
            System.out.println("Clearing all data...");
            db.clearAllData();

            // 3️⃣ Add a source file record
            System.out.println("Inserting source file record...");
            int fileId = db.addSourceFile("sample_text.txt", 120);
            System.out.println("Inserted source file with ID: " + fileId);

            // 4️⃣ Insert words individually
            System.out.println("Adding words...");
            Word the = new Word("the");
            the.setTotalOccurrences(10);
            the.setStartSentenceCount(3);

            Word cat = new Word("cat");
            cat.setTotalOccurrences(5);
            cat.setEndSequenceCount(2);

            db.addWord(the);
            db.addWord(cat);

            // 5️⃣ Query a word
            Word fetchedWord = db.getWord("cat");
            if (fetchedWord != null) {
                System.out.println("Fetched word: " + fetchedWord);
            } else {
                System.out.println("Word not found in database.");
            }

            // 6️⃣ Insert a word pair (e.g., "the cat")
            System.out.println("Adding word pair...");
            int theId = db.getWordId("the");
            int catId = db.getWordId("cat");

            if (theId > 0 && catId > 0) {
                WordPair pair = new WordPair(theId, catId);
                pair.setOccurrenceCount(3);
                int pairId = db.addWordPair(pair);
                System.out.println("Inserted/Updated word pair (sequence ID: " + pairId + ")");
            }

            // 7️⃣ Insert words in batch
            System.out.println("Adding batch of words...");
            List<Word> moreWords = List.of(
                    new Word("dog"),
                    new Word("sat"),
                    new Word("mat")
            );
            db.addWordsInBatch(moreWords);
            System.out.println("Batch insert complete.");

            // 8️⃣ Verify word IDs
            Map<String, Integer> wordIds = db.getWordIds(moreWords);
            System.out.println("Word IDs retrieved: " + wordIds);

            // 9️⃣ Bulk insert word pairs
            System.out.println("Adding bulk word pairs...");
            List<WordPair> wordPairs = new ArrayList<>();
            wordPairs.add(new WordPair(wordIds.get("dog"), wordIds.get("sat")));
            wordPairs.add(new WordPair(wordIds.get("sat"), wordIds.get("mat")));
            db.bulkAddWordPairs(wordPairs);
            System.out.println("Bulk word pairs added successfully.");

            // 🔟 Confirm a word pair update
            WordPair repeatPair = new WordPair(theId, catId);
            repeatPair.setOccurrenceCount(1);
            db.addWordPair(repeatPair);
            System.out.println("Incremented occurrence count for 'the cat'.");

            System.out.println("=== ✅ Database test completed successfully! ===");

        } catch (SQLException e) {
            System.err.println("❌ Database operation failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanly shut down Hikari connection pool
            DatabaseManager.closeDataSource();
        }
    }
}
