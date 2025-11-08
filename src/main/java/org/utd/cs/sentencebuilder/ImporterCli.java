/**
 * ImporterCli.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kevin Tran
 * Date: October 6, 2025
 *
 * Description:
 * Walks a folder (default: data/clean), tokenizes .txt files,
 * upserts words per file, then global bigrams using word IDs.
 */




package org.utd.cs.sentencebuilder;

import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ImporterCli
 * Walks a folder (default: data/clean), tokenizes .txt files,
 * upserts words per file, then (optionally) global bigrams using word IDs.
 *
 * Usage:
 *   mvn -q -DskipTests compile
 *   mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.ImporterCli
 *   mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.ImporterCli -Dexec.args="--words-only"
 *   mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.ImporterCli -Dexec.args="path/to/folder"
 */
public class ImporterCli {

    private final DatabaseManager db;

    public ImporterCli(DatabaseManager db) {
        this.db = db;
    }

    public void run(Path root, boolean wordsOnly) {
        System.out.println("Scanning: " + root.toAbsolutePath());
        System.out.println("Mode: " + (wordsOnly ? "WORDS ONLY" : "WORDS + BIGRAMS"));

        try {
            List<Path> files = listTextFiles(root);
            if (files.isEmpty()) {
                System.out.println("No .txt files found. Put cleaned sources in data/clean or pass a folder/file.");
                return;
            }

            // Get already-imported files
            Map<String, SourceFile> importedFilesMap;
            try {
                importedFilesMap = db.getAllSourceFiles();
                System.out.println("Found " + importedFilesMap.size() + " files already in database.");
            } catch (SQLException e) {
                System.err.println("CRITICAL: Could not retrieve existing file list from database. Aborting.");
                e.printStackTrace();
                return;
            }
            System.out.println("\n--- Accumulating Words & Sentences ---");
            Map<String, Word> globalWords = new HashMap<>();
            Map<String, Sentence> globalSentenceCount = new HashMap<>();
            Map<Integer, Integer> globalLengthHistogram = new HashMap<>();
            // ---- PER-FILE PASS ----
            for (Path p : files) {
                System.out.println("\n--- Processing: " + p.getFileName() + " ---");

                if (importedFilesMap.containsKey(p.getFileName().toString())) {
                    System.out.println("File already imported.");
                    continue; // Skip this file
                }

                String text = Files.readString(p);
                Tokenizer.Result r = Tokenizer.process(text, Tokenizer.Mode.WORDS_AND_SENTENCES);
                System.out.println("Tokens: " + r.tokens.size() + " | Unique words: " + r.words.size());

                // record source file row (non-fatal if it fails)
                try {
                    db.addSourceFile(p.getFileName().toString(), r.tokens.size());
                } catch (SQLException ex) {
                    System.err.println("addSourceFile failed for " + p.getFileName() + ": " + ex.getMessage());
                }


                // accumulate into global aggregates for one-time ID resolution
                mergeWords(globalWords, r.words);
                mergeSentences(globalSentenceCount, r.sentenceCounts);
                mergeCounts(globalLengthHistogram, r.sentenceLengthCounts);
            }

            // ---- AFTER LOOP: finalize inserts ----
            if (globalWords.isEmpty()) {
                System.out.println("\nNothing to insert (no words collected).");
                return;
            }


            // --- BATCH INSERT WORDS & SENTENCES ---
            try {
                System.out.println("\nSaving " + globalWords.size() + " unique words to database...");
                db.addWordsInBatch(globalWords.values());
            } catch (SQLException ex) {
                System.err.println("addWordsInBatch failed: " + ex.getMessage());
                ex.printStackTrace();
                return;
            }

            try {
                db.addSentencesInBatch(globalSentenceCount.values());
            } catch (SQLException ex) {
                System.err.println("addSentencesInBatch failed: " + ex.getMessage());
                ex.printStackTrace();
            }

            if (wordsOnly) {
                System.out.println("\nWords-only run complete.");
                return;
            }

            // Resolve word IDs once across the global set
            Map<String, Integer> wordIds;
            try {
                wordIds = db.getWordIds(globalWords.values());
                System.out.println("\nResolved " + wordIds.size() + " word IDs.");
            } catch (SQLException ex) {
                System.err.println("getWordIds failed: " + ex.getMessage());
                ex.printStackTrace();
                return;
            }

            // --- STREAM AND BATCH N-GRAMS PER FILE ---
            globalWords.clear();
            globalSentenceCount.clear();
            System.out.println("\n--- Streaming N-Grams ---");
            for (Path p : files) {
                if (importedFilesMap.containsKey(p.getFileName().toString())) {
                    continue; // Skip this file
                }

                String text = Files.readString(p);
                Tokenizer.Result r = Tokenizer.process(text, Tokenizer.Mode.NGRAMS);

                List<WordPair> pairs = toWordPairs(r.bigramCounts, r.bigramEndCounts, wordIds);
                List<WordTriplet> triplets = toWordTriplets(r.trigramCounts, r.trigramEndCounts, wordIds);

                if (!pairs.isEmpty()) {
                    System.out.println("  Inserting " + pairs.size() + " word pairs...");
                    try {
                        db.bulkAddWordPairs(pairs);
                    } catch (SQLException ex) {
                        System.err.println("  bulkAddWordPairs failed: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }

                if (!triplets.isEmpty()) {
                    System.out.println("  Inserting " + triplets.size() + " word triplets...");
                    try {
                        db.bulkAddWordTriplets(triplets);
                    } catch (SQLException ex) {
                        System.err.println("  bulkAddWordTriplets failed: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            }

            try {
                FeatureBuilder builder = new FeatureBuilder(db);
                builder.buildAllSentenceFeatures();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }



        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        boolean wordsOnly = Arrays.asList(args).contains("--words-only");
        Path root = Arrays.stream(args)
                .filter(a -> !a.startsWith("--"))
                .findFirst()
                .map(Path::of)
                .orElse(Path.of("data/clean"));

        // CLI mode: create the pool once, run, then close it.
        DatabaseManager db = new DatabaseManager();
        try {
            new ImporterCli(db).run(root, wordsOnly);
        } finally {
            DatabaseManager.closeDataSource();
        }
    }


    private static List<Path> listTextFiles(Path root) throws IOException {
        if (!Files.exists(root)) return List.of();
        if (Files.isRegularFile(root) && root.toString().toLowerCase().endsWith(".txt")) return List.of(root);
        try (var st = Files.walk(root)) {
            return st.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".txt"))
                     .sorted()
                     .collect(Collectors.toList());
        }
    }


    private static void mergeSentences(Map<String, Sentence> target, Map<String, Sentence> src) {
        // Loop over the map from the file (src)
        for (var entry : src.entrySet()) {
            String text = entry.getKey();
            Sentence from = entry.getValue(); // Sentence POJO from the file

            // Check if it's in the global map (target)
            Sentence into = target.get(text);

            if (into == null) {
                // Not in the global map yet.
                // Create a new POJO for the global map.
                into = new Sentence(from.getText(), from.getTokenCount());
                target.put(text, into);
            }

            // Add the counts from the file map to the global map's POJO
            into.setSentenceOccurrences(
                    into.getSentenceOccurrences() + from.getSentenceOccurrences()
            );
        }
    }

    private static void mergeWords(Map<String, Word> target, Map<String, Word> src) {
        for (var e : src.entrySet()) {
            String w = e.getKey();
            Word from = e.getValue();
            Word into = target.get(w);
            if (into == null) {
                into = new Word();
                into.setWordValue(w);
                into.setTotalOccurrences(0);
                into.setStartSentenceCount(0);
                into.setEndSequenceCount(0);
                target.put(w, into);
            }
            into.setTotalOccurrences(into.getTotalOccurrences() + from.getTotalOccurrences());
            into.setStartSentenceCount(into.getStartSentenceCount() + from.getStartSentenceCount());
            into.setEndSequenceCount(into.getEndSequenceCount() + from.getEndSequenceCount());
        }
    }

    /**
     * Merges the counts from a source map into a target map.
     * If a key exists in both maps, their integer values are summed.
     */
    private static <K> void mergeCounts(Map<K, Integer> target, Map<K, Integer> src) {
        if (src == null || target == null) {
            return;
        }
        // Iterate over every entry in the source map
        for (Map.Entry<K, Integer> e : src.entrySet()) {
            // Use merge() to add the value, or sum it if the key already exists
            target.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    private static List<WordPair> toWordPairs(Map<String,Integer> bigrams,
                                              Map<String,Integer> bigramEndCounts,
                                              Map<String, Integer> wordIds) {
        List<WordPair> out = new ArrayList<>();

        for (var entry : bigrams.entrySet()) {
            String bigramKey = entry.getKey();

            String[] parts = entry.getKey().split(" ");
            if (parts.length != 2) continue;  // sanity check

            Integer firstId = wordIds.get(parts[0]);
            Integer secondId = wordIds.get(parts[1]);
            if (firstId == null || secondId == null) continue;

            int biEndFrequency = bigramEndCounts.getOrDefault(bigramKey, 0);

            WordPair wp = new WordPair();
            wp.setPrecedingWordId(firstId);
            wp.setFollowingWordId(secondId);
            wp.setOccurrenceCount(entry.getValue());
            wp.setEndFrequency(biEndFrequency);
            out.add(wp);
        }
        return out;
    }


    //vincentphan
    private static List<WordTriplet> toWordTriplets(Map<String,Integer> trigrams,
                                                    Map<String, Integer> trigramEndCounts,
                                                    Map<String,Integer> wordIds) {
        List<WordTriplet> out = new ArrayList<>();

        for (var entry : trigrams.entrySet()) {
            String trigramKey = entry.getKey();

            String[] parts = entry.getKey().split(" ");
            if (parts.length != 3) continue;  // sanity check

            Integer firstId = wordIds.get(parts[0]);
            Integer secondId = wordIds.get(parts[1]);
            Integer thirdId = wordIds.get(parts[2]);
            if (firstId == null || secondId == null || thirdId == null) continue;

            int triEndFrequency = trigramEndCounts.getOrDefault(trigramKey, 0);

            WordTriplet wt = new WordTriplet();
            wt.setFirstWordId(firstId);
            wt.setSecondWordId(secondId);
            wt.setThirdWordId(thirdId);
            wt.setOccurrenceCount(entry.getValue());
            wt.setEndFrequency(triEndFrequency);
            out.add(wt);
        }
        return out;
    }

}
