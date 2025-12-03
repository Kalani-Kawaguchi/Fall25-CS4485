/**
 * ImporterCli.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kevin Tran & Vincent Phan
 * Date: October 6, 2025
 *
 * Description:
 * Walks a folder (default: data/clean), tokenizes .txt files,
 * upserts words per file, then global bigrams using word IDs.
 */


package org.utd.cs.sentencebuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

    private static final Logger logger = LoggerFactory.getLogger(ImporterCli.class);
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

            Map<String, Word> globalWords = new HashMap<>();
            Map<String, Integer> globalBigrams = new HashMap<>();
            Map<String, Integer> globalTrigrams = new HashMap<>();
            Map<String, Integer> globalBiEndCounts = new HashMap<>();
            Map<String, Integer> globalTriEndCounts = new HashMap<>();

            // ---- PER-FILE PASS ----
            for (Path p : files) {
                System.out.println("\n--- Processing: " + p.getFileName() + " ---");

                if (importedFilesMap.containsKey(p.getFileName().toString())) {
                    System.out.println("File already imported.");
                    continue; // Skip this file
                }

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
                    continue; // move to next file
                }

                // accumulate into global aggregates for one-time ID resolution
                mergeWords(globalWords, r.words);
                if (!wordsOnly) {
                    mergeCounts(globalBigrams, r.bigramCounts);
                    mergeCounts(globalTrigrams, r.trigramCounts);

                    mergeCounts(globalBiEndCounts, r.bigramEndCounts);
                    mergeCounts(globalTriEndCounts, r.trigramEndCounts);
                }
            }

            // ---- AFTER LOOP: finalize inserts ----
            if (globalWords.isEmpty()) {
                System.out.println("\nNothing to insert (no words collected).");
                return;
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

            List<WordPair> pairs = toWordPairs(globalBigrams, globalBiEndCounts, wordIds);
            System.out.println("Prepared " + pairs.size() + " word pairs. Inserting...");
            try {
                db.addWordPairsInBatch(pairs);
                System.out.println("Inserted bigrams.");
            } catch (SQLException ex) {
                System.err.println("bulkAddWordPairs failed: " + ex.getMessage());
                ex.printStackTrace();
            }

            List<WordTriplet> triplets = toWordTriplets(globalTrigrams, globalTriEndCounts, wordIds);
            System.out.println("Prepared " + pairs.size() + " word triplets. Inserting...");
            try {
                db.addWordTripletsInBatch(triplets);
                System.out.println("Inserted Trigrams.");
            } catch (SQLException ex) {
                System.err.println("bulkAddWordTrigrams failed: " + ex.getMessage());
                ex.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Vincent Phan
     * Experimental. Upload instantly rather than accumulating globally.
     */
    public void runStreaming(Path root, ProgressCallback callback) throws SQLException, IOException {
        List<Path> files = listTextFiles(root);
        logger.info("Import start for " + files.size() + " files.");

        // 2. Get existing files from DB to determine what actually needs work
        Map<String, SourceFile> existingFiles = db.getAllSourceFiles();

        // 3. Filter the list (The "ToDo" list)
        List<Path> filesToProcess = new ArrayList<>();
        for (Path p : files) {
            if (!existingFiles.containsKey(p.getFileName().toString())) {
                filesToProcess.add(p);
            }
        }

        int total = filesToProcess.size();
        logger.info("Found {} total files. {} new files to process.", files.size(), total);

        if (total == 0) {
            if (callback != null) callback.update(1.0, "No new files to import.");
            return;
        }

        try {
            Map<String, SourceFile> importedFiles = db.getAllSourceFiles();
            for (int i = 0; i < total; i++) {

                Path file = filesToProcess.get(i);
                String fileName = file.getFileName().toString();

                if (callback != null) {
                    double percent = (double) i / total;
                    callback.update(percent, "Importing: " + fileName);
                }

                logger.info("Processing " + file.getFileName());

                String text = Files.readString(file);
                Tokenizer.Result r = Tokenizer.process(text, Tokenizer.Mode.ALL);

                if (r.words.isEmpty()) {
                    logger.warn(file.getFileName() + "has no words. Skipping file.");
                    continue;
                }

                db.addWordsInBatch(r.words.values());
                db.addSentencesInBatch(r.sentenceCounts.values());
                db.addSentenceLengthsInBatch(r.sentenceLengthCounts);

                Map<String, Integer> wordIds = db.getWordIds(r.words.values());


                // Convert and insert bigrams immediately
                List<WordPair> pairs = toWordPairs(r.bigramCounts, r.bigramEndCounts, wordIds);
                if (!pairs.isEmpty()) db.addWordPairsInBatch(pairs);

                // Convert and insert trigrams immediately
                List<WordTriplet> trips = toWordTriplets(r.trigramCounts, r.trigramEndCounts, wordIds);
                if (!trips.isEmpty()) db.addWordTripletsInBatch(trips);

                db.addSourceFile(file.getFileName().toString(), r.tokens.size());

                // Free memory explicitly
                r.words.clear();
                r.sentenceCounts.clear();
                r.bigramCounts.clear();
                r.bigramEndCounts.clear();
                r.trigramCounts.clear();
                r.trigramEndCounts.clear();

                System.gc(); // optional force garbage collect
            }
            // remove any serious outliers (run-on sentences)
            db.pruneOutliers(.99);
            db.recomputeLengthHazards();
            logger.info("Import complete");

        } catch (Exception e) {
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
