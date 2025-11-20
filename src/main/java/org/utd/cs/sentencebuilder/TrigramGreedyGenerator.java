/**
 *  Author: Kevin Tran
 *  Modified for Trigrams: Renz Padlan
 *  CS4485. Fall 2025
 *  November 19 2025
 *  
 *  Description:
 *  Greedy trigram sentence generator.
 *
 *  Uses two-word context:
 *      (w1, w2) → most frequent follower w3
 *  Data sourced from trigram_sequence table and shared through
 *  GeneratorDataController.
 *
 *  Supports optional starting word(s) and maximum token limits.
 */


package org.utd.cs.sentencebuilder;

import java.util.*;

/**
 * Greedy trigram generator.
 * Uses 2-word context (w1, w2) -> most frequent follower w3.
 */
public class TrigramGreedyGenerator implements SentenceGenerator {

    private static final int DEFAULT_MAX_TOKENS = 20;

    private final Map<String, Integer> wordToId;
    private final Map<Integer, String> idToWord;
    // (w1,w2) encoded as long -> list of (w3, count), sorted desc by count
    private final Map<Long, List<int[]>> followers;

    /** Given a first word id, pick a good second id so (w1,w2) is a valid trigram context. */
    private Integer pickGreedySecondGivenFirst(int firstId) {
        int bestSecond = -1;
        int bestTotal  = -1;

        for (Map.Entry<Long, List<int[]>> e : followers.entrySet()) {
            long key = e.getKey();
            int w1 = (int) (key >> 32);
            if (w1 != firstId) continue;

            int w2 = (int) key;

            int total = 0;
            for (int[] arr : e.getValue()) {
                total += arr[1];
            }

            if (total > bestTotal) {
                bestTotal  = total;
                bestSecond = w2;
            }
        }

        return (bestSecond == -1) ? null : bestSecond;
    }

    public TrigramGreedyGenerator(Map<String, Integer> wordToId,
                                  Map<Integer, String> idToWord,
                                  Map<Long, List<int[]>> trigramFollowers,
                                  List<int[]> startCandidates) {
        this.wordToId        = wordToId;
        this.idToWord        = idToWord;
        this.followers       = trigramFollowers;
    }

    @Override
    public String getName() {
        return "TrigramGreedyGenerator";
    }

    @Override
    public String generateSentence() {
        return generateSentence(DEFAULT_MAX_TOKENS, null);
    }

    @Override
    public String generateSentence(List<String> startingWords) {
        return generateSentence(startingWords, DEFAULT_MAX_TOKENS, null);
    }

    // no-seed path: pick 2 likely start words
    @Override
    public String generateSentence(int maxTokens, String stopWord) {
        List<Integer> startIds = pickStartFromTrigramFollowers();
        if (startIds.size() < 2) return "";
        return buildGreedySentence(startIds, maxTokens, stopWord);
    }

    // with seed (string-based, for interface)
    @Override
    public String generateSentence(List<String> startingWords,
                                   int maxTokens,
                                   String stopWord) {

        // If no seed provided, just pick any trigram start
        if (startingWords == null || startingWords.isEmpty()) {
            List<Integer> fromTri = pickStartFromTrigramFollowers();
            if (fromTri.size() < 2) return "";
            return buildGreedySentence(fromTri, maxTokens, stopWord);
        }

        Integer firstId = null;
        Integer secondId = null;

        if (startingWords.get(0) != null && !startingWords.get(0).isBlank()) {
            firstId = wordToId.get(startingWords.get(0).toLowerCase(Locale.ROOT));
        }

        if (startingWords.size() > 1 &&
            startingWords.get(1) != null &&
            !startingWords.get(1).isBlank()) {

            Integer maybeSecond = wordToId.get(startingWords.get(1).toLowerCase(Locale.ROOT));
            if (firstId != null && maybeSecond != null &&
                followers.containsKey(makePairKey(firstId, maybeSecond))) {
                secondId = maybeSecond;
            }
        }

        if (firstId != null && secondId == null) {
            secondId = pickGreedySecondGivenFirst(firstId);
        }

        List<Integer> startIds;
        if (firstId == null || secondId == null) {
            startIds = pickStartFromTrigramFollowers();
        } else {
            startIds = List.of(firstId, secondId);
        }

        if (startIds.size() < 2) return "";
        return buildGreedySentence(startIds, maxTokens, stopWord);
    }

    /**
     * ID-based entry point used by controller/CLI.
     * startingIds: first element is w1, second (optional) is w2.
     * If we only get w1, we pick a good w2 via pickGreedySecondGivenFirst.
     */
    public String generateFromIds(List<Integer> startingIds,
                                  int maxTokens,
                                  String stopWord) {

        Integer firstId  = null;
        Integer secondId = null;

        if (startingIds != null && !startingIds.isEmpty()) {
            firstId = startingIds.get(0);
            if (startingIds.size() > 1) {
                secondId = startingIds.get(1);
            }
        }

        if (firstId != null && secondId != null) {
            long key = makePairKey(firstId, secondId);
            if (!followers.containsKey(key)) {
                // invalid (w1,w2) pair, treat as if we only had w1
                secondId = null;
            }
        }

        if (firstId != null && secondId == null) {
            secondId = pickGreedySecondGivenFirst(firstId);
        }

        List<Integer> startIds;
        if (firstId == null || secondId == null) {
            startIds = pickStartFromTrigramFollowers();
        } else {
            startIds = List.of(firstId, secondId);
        }

        if (startIds.size() < 2) return "";
        return buildGreedySentence(startIds, maxTokens, stopWord);
    }

    /** Pick a starting (w1, w2) pair that definitely exists in trigramFollowers. */
    private List<Integer> pickStartFromTrigramFollowers() {
        if (followers.isEmpty()) {
            return List.of();
        }

        long key = followers.keySet().iterator().next();
        int w1 = (int) (key >> 32);
        int w2 = (int) key;
        return List.of(w1, w2);
    }

    // ---------- internals ----------

    private String buildGreedySentence(List<Integer> seed, int maxTokens, String stopWordRaw) {
        if (seed.size() < 2) return "";

        final String stopWord = (stopWordRaw == null) ? null : stopWordRaw.toLowerCase(Locale.ROOT);

        List<Integer> ids = new ArrayList<>(seed);
        Set<Integer> visited = new HashSet<>(ids);  // simple loop guard

        int secondLast = ids.get(ids.size() - 2);
        int last       = ids.get(ids.size() - 1);

        Set<String> usedTrigrams = new HashSet<>();

        while (ids.size() < Math.max(2, maxTokens)) {
            long pairKey = makePairKey(secondLast, last);
            List<int[]> cands = followers.get(pairKey);

            if (cands == null || cands.isEmpty()) {
                break;
            }

            int nextId = -1;
            for (int[] cand : cands) {
                int candId = cand[0];

                String trigramSig = secondLast + "-" + last + "-" + candId;
                if (usedTrigrams.contains(trigramSig)) continue;

                if (candId == last) continue;        // avoid x y y
                if (candId == secondLast) continue;  // avoid x y x

                nextId = candId;
                usedTrigrams.add(trigramSig);
                break;
            }

            if (nextId == -1) break;

            ids.add(nextId);
            visited.add(nextId);

            // slide the window
            secondLast = last;
            last       = nextId;

            if (stopWord != null) {
                String w = idToWord.getOrDefault(last, "").toLowerCase(Locale.ROOT);
                if (w.equals(stopWord)) break;
            }
        }

        return render(ids);
    }

    private static long makePairKey(int w1, int w2) {
        return (((long) w1) << 32) | (w2 & 0xffffffffL);
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

    public String generateSentenceWithStop(String stopWord) {
        return generateSentence(DEFAULT_MAX_TOKENS, stopWord);
    }
}

