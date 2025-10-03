/**
 * Tokenizer.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kevin Tran
 * Date: October 3 2025
 *
 * Description:
 *  Prototype tokenizer that reads text files, normalizes words,
 *  and builds unigram + bigram counts in memory.
 */

package org.utd.cs.sentencebuilder;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class Tokenizer {

    private static final Pattern EDGE_PUNCT = Pattern.compile("^[^\\p{L}\\p{Nd}']+|[^\\p{L}\\p{Nd}']+$");

    public static class Result {
        public final List<String> tokens = new ArrayList<>();
        public final Map<String, Integer> unigrams = new HashMap<>();
        public final Map<String, Map<String, Integer>> bigrams = new HashMap<>();
    }

    public static Result processFile(String path) throws IOException {
        String text = Files.readString(Path.of(path));
        return process(text);
    }

    public static Result process(String text) {
        Result r = new Result();

        // tokenize
        String[] raw = text.split("\\s+");
        for (String t : raw) {
            if (t.isBlank()) continue;
            String cleaned = EDGE_PUNCT.matcher(t).replaceAll("");
            if (cleaned.isEmpty()) continue;
            String w = cleaned.toLowerCase(Locale.ROOT);
            r.tokens.add(w);
        }

        // count unigrams
        for (String w : r.tokens) {
            r.unigrams.merge(w, 1, Integer::sum);
        }

        // count bigrams
        for (int i = 0; i + 1 < r.tokens.size(); i++) {
            String prev = r.tokens.get(i);
            String next = r.tokens.get(i + 1);
            r.bigrams.computeIfAbsent(prev, k -> new HashMap<>())
                     .merge(next, 1, Integer::sum);
        }
        return r;
    }

    public static List<Map.Entry<String,Integer>> topK(Map<String,Integer> m, int k) {
        return m.entrySet().stream()
                .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
                .limit(k).toList();
    }
}
