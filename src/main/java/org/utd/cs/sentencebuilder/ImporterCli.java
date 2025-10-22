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

    public static void main(String[] args) {
        boolean wordsOnly = Arrays.asList(args).contains("--words-only");
        Path root = Arrays.stream(args)
                .filter(a -> !a.startsWith("--"))
                .findFirst()
                .map(Path::of)
                .orElse(Path.of("data/clean"));

        System.out.println("Scanning: " + root.toAbsolutePath());
        System.out.println("Mode: " + (wordsOnly ? "WORDS ONLY" : "WORDS + BIGRAMS"));

        DatabaseManager db = null;
        try {
            List<Path> files = listTextFiles(root);
            if (files.isEmpty()) {
                System.out.println("No .txt files found. Put cleaned sources in data/clean or pass a folder/file.");
                return;
            }

            db = new DatabaseManager();

            // Vincent Phan
            // Get SourceFiles already in db as a hashmap.
            Map<String, SourceFile> importedFilesMap;
            try {
                importedFilesMap = db.getAllSourceFiles();
                System.out.println("Found " + importedFilesMap.size() + " files already in database.");
            } catch (SQLException e) {
                System.err.println("CRITICAL: Could not retrieve existing file list from database. Aborting.");
                e.printStackTrace();
                return; // Can't safely continue if this fails
            }
            //end

            // Accumulate across ALL files (we resolve IDs once at the end)
            Map<String, Word> globalWords = new HashMap<>();
            Map<String, Map<String, Integer>> globalBigrams = new HashMap<>();

            for (Path p : files) {
                System.out.println("\n--- Processing: " + p.getFileName() + " ---");

                // Vincent Phan
                if (importedFilesMap.containsKey(p.getFileName().toString())) {
                    System.out.println("File already imported.");
                    continue; // Skip all processing for this file
                }
                //end


                String text = Files.readString(p);
                Tokenizer.Result r = Tokenizer.process(text);

                System.out.println("Tokens: " + r.tokens.size() + " | Unique words: " + r.words.size());

                // record source file row (non-fatal if it fails)
                try {
                    db.addSourceFile(p.getFileName().toString(), r.tokens.size());
                } catch (SQLException ex) {
                    System.err.println("addSourceFile failed for " + p.getFileName() + ": " + ex.getMessage());
                }

                // upsert words for THIS file
                try {
                    db.addWordsInBatch(r.words.values());
                } catch (SQLException ex) {
                    System.err.println("addWordsInBatch failed for " + p.getFileName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    // continue to next file
                    continue;
                }

                // accumulate into global aggregates for one-time ID resolution
                mergeWords(globalWords, r.words);
                if (!wordsOnly) {
                    mergeBigrams(globalBigrams, r.bigramCounts);
                }
            }

            if (globalWords.isEmpty()) {
                System.out.println("\nNothing to insert (no words collected).");
                return;
            }

            if (wordsOnly) {
                System.out.println("\nWords-only run complete.");
                return;
            }

            // ---- BIGRAMS PATH ----
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

            // Convert (prev->next->count) into WordPair objects with IDs and insert
            List<WordPair> pairs = toWordPairs(globalBigrams, wordIds);
            System.out.println("Prepared " + pairs.size() + " word pairs. Inserting...");
            try {
                db.bulkAddWordPairs(pairs);
                System.out.println("Inserted bigrams.");
            } catch (SQLException ex) {
                System.err.println("bulkAddWordPairs failed: " + ex.getMessage());
                ex.printStackTrace();
            }

            System.out.println("\nDone.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // always release the pool cleanly
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

    private static void mergeBigrams(Map<String, Map<String,Integer>> target,
                                     Map<String, Map<String,Integer>> src) {
        for (var e : src.entrySet()) {
            String prev = e.getKey();
            Map<String,Integer> intoRow = target.computeIfAbsent(prev, k -> new HashMap<>());
            for (var n : e.getValue().entrySet()) {
                intoRow.merge(n.getKey(), n.getValue(), Integer::sum);
            }
        }
    }

    private static List<WordPair> toWordPairs(Map<String, Map<String,Integer>> bigrams,
                                              Map<String,Integer> wordIds) {
        List<WordPair> out = new ArrayList<>();
        for (var prevEntry : bigrams.entrySet()) {
            Integer prevId = wordIds.get(prevEntry.getKey());
            if (prevId == null) continue;
            for (var nextEntry : prevEntry.getValue().entrySet()) {
                Integer nextId = wordIds.get(nextEntry.getKey());
                if (nextId == null) continue;
                WordPair wp = new WordPair();
                wp.setPrecedingWordId(prevId);
                wp.setFollowingWordId(nextId);
                wp.setOccurrenceCount(nextEntry.getValue());
                out.add(wp);
            }
        }
        return out;
    }
}
