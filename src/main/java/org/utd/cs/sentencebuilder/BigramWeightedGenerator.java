/**
 *  Author: Kevin Tran
 *  CS4485. Fall 2025
 *  November 14 2025
 *
 *  Description:
 *  Implements weighted-random bigram sentence generation. Instead of
 *  choosing the most frequent follower, selects followers proportionally
 *  to their observed frequency counts.
 *
 *  Features:
 *    - Weighted random start-word selection
 *    - Weighted random next-token selection
 *    - Optional seed, stop word, and max token limit
 *    - Shares cached data via GeneratorDataController
 *
 *  This model produces diverse outputs and avoids deterministic behavior
 *  while still respecting statistical frequencies from the corpus.
 */

package org.utd.cs.sentencebuilder;

import java.util.*;

public class BigramWeightedGenerator implements SentenceGenerator {

    private static final int DEFAULT_MAX_TOKENS = 20;

    private final Map<String, Integer> wordToId;
    private final Map<Integer, String> idToWord;
    private final Map<Integer, List<int[]>> followers;
    private final List<int[]> startCandidates;
    private final Random random = new Random();

    public BigramWeightedGenerator(Map<String, Integer> wordToId,
                                   Map<Integer, String> idToWord,
                                   Map<Integer, List<int[]>> followers,
                                   List<int[]> startCandidates) {
        this.wordToId = wordToId;
        this.idToWord = idToWord;
        this.followers = followers;
        this.startCandidates = startCandidates;
    }

    @Override
    public String getName() {
        return "BigramWeightedGenerator";
    }

    @Override
    public String generateSentence() {
        return generateSentence(DEFAULT_MAX_TOKENS, null);
    }

    @Override
    public String generateSentence(List<String> startingWords) {
        return generateSentence(startingWords, DEFAULT_MAX_TOKENS, null);
    }

    @Override
    public String generateSentence(int maxTokens, String stopWord) {
        Integer startId = pickWeightedStartId();
        if (startId == null) return "";
        return buildSentence(List.of(startId), maxTokens, stopWord);
    }

    @Override
    public String generateSentence(List<String> startingWords, int maxTokens, String stopWord) {
        Integer startId = null;

        if (startingWords != null) {
            for (String w : startingWords) {
                if (w == null || w.isBlank()) continue;
                Integer id = wordToId.get(w.toLowerCase(Locale.ROOT));
                if (id != null) {
                    startId = id;
                    break;
                }
            }
        }

        if (startId == null) startId = pickWeightedStartId();
        if (startId == null) return "";

        return buildSentence(List.of(startId), maxTokens, stopWord);
    }

    /**
     * ID-based entry point for the weighted bigram generator.
     */
    public String generateFromIds(List<Integer> startingIds,
                                  int maxTokens,
                                  String stopWord) {
        Integer startId = null;

        if (startingIds != null && !startingIds.isEmpty()) {
            startId = startingIds.get(0);
        }
        if (startId == null) {
            startId = pickWeightedStartId();
        }
        if (startId == null) return "";

        return buildSentence(List.of(startId), maxTokens, stopWord);
    }

    // ---------- internals ----------

    private Integer pickWeightedStartId() {
        if (startCandidates.isEmpty()) {
            return idToWord.isEmpty() ? null : idToWord.keySet().iterator().next();
        }

        int total = 0;
        for (int[] c : startCandidates) total += c[1];

        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int[] c : startCandidates) {
            cumulative += c[1];
            if (roll < cumulative) return c[0];
        }

        return startCandidates.get(0)[0];
    }

    private String buildSentence(List<Integer> seed, int maxTokens, String stopWordRaw) {
        final String stopWord = (stopWordRaw == null) ? null : stopWordRaw.toLowerCase(Locale.ROOT);

        List<Integer> ids = new ArrayList<>(seed);
        Set<Integer> visited = new HashSet<>(ids);
        int curr = ids.get(ids.size() - 1);

        while (ids.size() < Math.max(1, maxTokens)) {
            List<int[]> cands = followers.get(curr);
            if (cands == null || cands.isEmpty()) break;

            int nextId = chooseWeightedNext(cands);
            if (visited.contains(nextId)) break;

            ids.add(nextId);
            visited.add(nextId);
            curr = nextId;

            if (stopWord != null) {
                String w = idToWord.getOrDefault(curr, "").toLowerCase(Locale.ROOT);
                if (w.equals(stopWord)) break;
            }
        }

        return render(ids);
    }

    private int chooseWeightedNext(List<int[]> cands) {
        int total = 0;
        for (int[] c : cands) total += c[1];

        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int[] c : cands) {
            cumulative += c[1];
            if (roll < cumulative) return c[0];
        }

        return cands.get(0)[0];
    }

    private String render(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            String w = idToWord.getOrDefault(id, "?");
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }
}
