/**
 * Tokenizer.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kevin Tran & Vincent Phan
 * Date: October 3 2025
 *
 * Description:
 *  Tokenizes text files, producing:
 *   - Map<String, Word>  (Word object holds total/start/end counts)
 *   - Map<String, Map<String, Integer>> bigramCounts (prev -> next -> count)
 *
 *  Bigram and Trigram counts are flattened for faster reducing.
 */

package org.utd.cs.sentencebuilder;

import java.nio.file.*;
import java.io.IOException;
import java.text.BreakIterator;
import java.util.*;
import java.util.regex.Pattern;

public class Tokenizer {

    /**
     *
     */
    public enum Mode {
      WORDS_AND_SENTENCES, NGRAMS, ALL
    }

    //only used by depecrated function.
    private static final Pattern EDGE_PUNCT = Pattern.compile("^[^\\p{L}\\p{Nd}']+|[^\\p{L}\\p{Nd}']+$");

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    public static class Result {
        /** Word string -> Word object (with total/start/end counts). */
        public final Map<String, Word> words = new HashMap<>();
        /** Counts: n-gram -> count. */
        Map<String, Integer> bigramCounts = new HashMap<>();    // "w1 w2" -> count
        Map<String, Integer> trigramCounts = new HashMap<>();   // "w1 w2 w3" -> count

        /** Counts: sentence text -> Sentence object. */
        public Map<String, Sentence> sentenceCounts = new HashMap<>();

        /** Bigram/trigram end-of-sentence counts. */
        public final Map<String, Integer> bigramEndCounts = new HashMap<>();
        public final Map<String, Integer> trigramEndCounts = new HashMap<>();

        /** Flat list of tokens (mostly for debugging/printing). */
        public final List<String> tokens = new ArrayList<>();
    }

    public static Result processFile(String path) throws IOException {
        String text = Files.readString(Path.of(path));
        return process(text, Mode.ALL);
    }

    /**
     * Processes text, extracting all features.
     * @param text The raw text to process.
     * @return The complete Result object.
     */
    public static Result process(String text) {
        return process(text, Mode.ALL);
    }

    /**
     * Processes text according to the specified mode.
     * @param text The raw text to process.
     * @param mode The features to extract.
     * @return A Result object, potentially partially populated.
     */
    public static Result process(String text, Mode mode) {
        Result r = new Result();

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        boolean doWords = (mode == Mode.ALL || mode == Mode.WORDS_AND_SENTENCES);
        boolean doNgrams = (mode == Mode.ALL || mode == Mode.NGRAMS);

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentenceText = text.substring(start, end).trim();
            if (sentenceText.isEmpty()) continue;

            List<String> toks = tokenizeSentence(sentenceText);
            if (toks.isEmpty()) continue;

            r.tokens.addAll(toks);

            if (doWords) {
                Sentence newSentence = r.sentenceCounts.get(sentenceText);
                if (newSentence == null) {
                    // First time seeing this sentence *in this file*
                    int tokenCount = toks.size();
                    newSentence = new Sentence(sentenceText, tokenCount);
                    r.sentenceCounts.put(sentenceText, newSentence);
                }

                //kevin
                for (int i = 0; i < toks.size(); i++) {
                    String w = toks.get(i);
                    Word obj = r.words.computeIfAbsent(w, k -> {
                        Word nw = new Word();
                        nw.setWordValue(k);
                        nw.setTotalOccurrences(0);
                        nw.setStartSentenceCount(0);
                        nw.setEndSequenceCount(0);
                        return nw;
                    });
                    obj.setTotalOccurrences(obj.getTotalOccurrences() + 1);
                    if (i == 0) obj.setStartSentenceCount(obj.getStartSentenceCount() + 1);
                    if (i == toks.size() - 1)
                        obj.setEndSequenceCount(obj.getEndSequenceCount() + 1);
                }
            }

            if (doNgrams) {
                //kevin & vincent
                for (int i = 0; i < toks.size(); i++) {
                    String w1 = toks.get(i);
                    //bigram&trigram logic
                    if (i + 1 < toks.size()) {
                        String w2 = toks.get(i + 1);
                        String bigramKey = w1 + " " + w2;
                        r.bigramCounts.merge(bigramKey, 1, Integer::sum);

                        // End-of-sentence bigram
                        if (i + 2 == toks.size()) {
                            r.bigramEndCounts.merge(bigramKey, 1, Integer::sum);
                        }

                        //trigram
                        if (i + 2 < toks.size()) {
                            String w3 = toks.get(i + 2);
                            String trigramKey = w1 + " " + w2 + " " + w3;

                            r.trigramCounts.merge(trigramKey, 1, Integer::sum);

                            // End-of-sentence trigram
                            if (i + 3 == toks.size()) {
                                r.trigramEndCounts.merge(trigramKey, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }

        return r;
    }

    /** Tokenize a single sentence (clean punctuation, lowercase). */
    private static List<String> tokenizeSentence(String sent) {
        List<String> toks = new ArrayList<>();
        for (String raw : sent.split("\\s+")) {
            if (raw == null || raw.isBlank()) continue;
            String cleaned = EDGE_PUNCT.matcher(raw).replaceAll("");
            if (cleaned.isEmpty()) continue;
            toks.add(cleaned.toLowerCase(Locale.ROOT));
        }
        return toks;
    }

    // deprecated but keeping around just in case?
    /** Naive sentence boundary marking: split on ., !, ? (keeps it simple for v1). */
    private static void markSentenceStartsAndEnds(Map<String, Word> wordMap, String fullText) {
        // Split on punctuation followed by whitespace. Then increment start/end counts on first/last token of each segment.
        String[] sents = fullText.split("(?<=[.!?])\\s+");
        for (String sent : sents) {
            List<String> toks = new ArrayList<>();
            for (String raw : sent.split("\\s+")) {
                if (raw.isBlank()) continue;
                String cleaned = EDGE_PUNCT.matcher(raw).replaceAll("");
                if (cleaned.isEmpty()) continue;
                toks.add(cleaned.toLowerCase(Locale.ROOT));
            }
            if (toks.isEmpty()) continue;

            String start = toks.get(0);
            String end   = toks.get(toks.size() - 1);

            Word startW = wordMap.get(start);
            if (startW != null) startW.setStartSentenceCount(startW.getStartSentenceCount() + 1);

            Word endW = wordMap.get(end);
            if (endW != null) endW.setEndSequenceCount(endW.getEndSequenceCount() + 1);
        }
    }

    /** Utility: top-k by value for maps (for CLI printing). */
    public static List<Map.Entry<String,Integer>> topK(Map<String,Integer> m, int k) {
        return m.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(k)
                .toList();
    }

    /** Build a temporary unigram-count map (String -> count) from the Word map (for printing/testing). */
    public static Map<String,Integer> toUnigramCountMap(Map<String, Word> words) {
        Map<String,Integer> out = new HashMap<>();
        for (Map.Entry<String, Word> e : words.entrySet()) {
            out.put(e.getKey(), e.getValue().getTotalOccurrences());
        }
        return out;
    }

}