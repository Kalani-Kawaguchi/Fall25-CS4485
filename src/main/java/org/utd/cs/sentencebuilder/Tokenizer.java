/**
 * Tokenizer.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kevin Tran
 * Date: October 3 2025
 *
 * Description:
 *  Tokenizes text files, producing:
 *   - Map<String, Word>  (Word object holds total/start/end counts)
 *   - Map<String, Map<String, Integer>> bigramCounts (prev -> next -> count)
 *
 *  We purposely keep bigram counts keyed by strings here because WordPair
 *  needs word IDs, which we won’t have until after words are inserted in DB.
 */

package org.utd.cs.sentencebuilder;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class Tokenizer {

    private static final Pattern EDGE_PUNCT = Pattern.compile("^[^\\p{L}\\p{Nd}']+|[^\\p{L}\\p{Nd}']+$");

    public static class Result {
        /** Word string -> Word object (with total/start/end counts). */
        public final Map<String, Word> words = new HashMap<>();
        /** Bigram counts: prevWord -> (nextWord -> count). */
        public final Map<String, Map<String, Integer>> bigramCounts = new HashMap<>();
        /** Flat list of tokens (mostly for debugging/printing). */
        public final List<String> tokens = new ArrayList<>();
    }

    public static Result processFile(String path) throws IOException {
        String text = Files.readString(Path.of(path));
        return process(text);
    }

public static Result process(String text) {
        Result r = new Result();

        // 1) tokenize into normalized tokens
        String[] raw = text.split("\\s+");
        for (String t : raw) {
            if (t == null || t.isBlank()) continue;
            String cleaned = EDGE_PUNCT.matcher(t).replaceAll("");
            if (cleaned.isEmpty()) continue;
            String w = cleaned.toLowerCase(Locale.ROOT);
            r.tokens.add(w);
        }

        if (r.tokens.isEmpty()) return r;

        // 2) unigram counts using Word objects
        //    Word has fields: wordValue, totalOccurrences, startSentenceCount, endSequenceCount
        //    We’ll bump total for every token. For now (no robust sentence split),
        //    we’ll approximate sentence boundaries with simple punctuation check on the raw text later if needed.
        for (int i = 0; i < r.tokens.size(); i++) {
            String w = r.tokens.get(i);
            Word obj = r.words.get(w);
            if (obj == null) {
                obj = new Word();
                obj.setWordValue(w);
                obj.setTotalOccurrences(0);
                obj.setStartSentenceCount(0);
                obj.setEndSequenceCount(0);
                r.words.put(w, obj);
            }
            obj.setTotalOccurrences(obj.getTotalOccurrences() + 1);
        }

        // Optional: VERY simple sentence boundary approximation (can upgrade later):
        // treat '.', '!', '?' in original string as boundaries and bump start/end counters
        markSentenceStartsAndEnds(r.words, text);

        // 3) bigram counts (string-string for now)
        for (int i = 0; i + 1 < r.tokens.size(); i++) {
            String prev = r.tokens.get(i);
            String next = r.tokens.get(i + 1);
            r.bigramCounts
                    .computeIfAbsent(prev, k -> new HashMap<>())
                    .merge(next, 1, Integer::sum);
        }

        return r;
    }

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