/**
 *  Author: Kevin Tran
 *  Modified for Trigrams: Renz Padlan
 *  CS4485. Fall 2025
 *  November 2025
 *
 *  Description:
 *  Implements a weighted random trigram-based sentence generation algorithm based of 
 *  the previous weighted random bigram algorithm
 *
 *  - Now Loads all words and trigrams from the database (via DatabaseManager)
 *  - Chooses a random start word weighted by start_sentence_count
 *  - For each TWO-word sequence, selects the next word based on weighted probability (occurrence_count)
 *  - Stops after max token limit or when no valid follower exists
 *  - Provides better context and more varied output than bigram weighted model
 */

package org.utd.cs.sentencebuilder;

import java.util.*;

public class TrigramWeightedGenerator implements SentenceGenerator {

    private static final int DEFAULT_MAX_TOKENS = 20;
    private final Random random = new Random();

    private final Map<String, Integer> wordToId;
    private final Map<Integer, String> idToWord;
    private final Map<Long, List<int[]>> followers;

    public TrigramWeightedGenerator(Map<String, Integer> wordToId,
                                    Map<Integer, String> idToWord,
                                    Map<Long, List<int[]>> trigramFollowers,
                                    List<int[]> startCandidates) {
        this.wordToId        = wordToId;
        this.idToWord        = idToWord;
        this.followers       = trigramFollowers;
    }

    /** Weighted choice of secondId given firstId, based on total trigram counts. */
    private Integer pickWeightedSecondGivenFirst(int firstId) {
        // secondId -> total count over all followers
        Map<Integer, Integer> secondTotals = new HashMap<>();

        for (Map.Entry<Long, List<int[]>> e : followers.entrySet()) {
            long key = e.getKey();
            int w1 = (int) (key >> 32);
            if (w1 != firstId) continue;

            int w2 = (int) key;
            int total = 0;
            for (int[] arr : e.getValue()) {
                total += arr[1];
            }
            secondTotals.merge(w2, total, Integer::sum);
        }

        if (secondTotals.isEmpty()) return null;

        int sum = secondTotals.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(sum);
        int cumulative = 0;

        for (Map.Entry<Integer,Integer> e : secondTotals.entrySet()) {
            cumulative += e.getValue();
            if (roll < cumulative) return e.getKey();
        }
        // Fallback
        return secondTotals.keySet().iterator().next();
    }

    
    /** Pick a start pair (w1, w2) from trigramFollowers. Currently uniform over keys. */
    private List<Integer> pickStartFromTrigramFollowers() {
        if (followers.isEmpty()) {
            return List.of();
        }


        long key = followers.keySet().iterator().next();
        int w1 = (int) (key >> 32);
        int w2 = (int) key;
        return List.of(w1, w2);
    }


    @Override
    public String getName() {
        return "TrigramWeightedGenerator";
    }

    @Override
    public String generateSentence() {
        return generateSentence(DEFAULT_MAX_TOKENS, null);
    }

    @Override
    public String generateSentence(List<String> startingWords) {
        return generateSentence(startingWords, DEFAULT_MAX_TOKENS, null);
    }

    public String generateSentence(List<String> startingWords,
                                   int maxTokens,
                                   String stopWord) {

        // No seed -> pure trigram start
        if (startingWords == null || startingWords.isEmpty()) {
            List<Integer> fromTri = pickStartFromTrigramFollowers();
            if (fromTri.size() < 2) return "";
            return buildSentence(fromTri, maxTokens, stopWord);
        }

        Integer firstId = null;
        Integer secondId = null;

        if (startingWords.get(0) != null && !startingWords.get(0).isBlank()) {
            firstId = wordToId.get(startingWords.get(0).toLowerCase(Locale.ROOT));
        }

        // Optional second seed word
        if (startingWords.size() > 1 &&
            startingWords.get(1) != null &&
            !startingWords.get(1).isBlank()) {

            Integer maybeSecond = wordToId.get(startingWords.get(1).toLowerCase(Locale.ROOT));
            if (firstId != null && maybeSecond != null &&
                followers.containsKey(makePairKey(firstId, maybeSecond))) {
                secondId = maybeSecond;
            }
        }

        // If we only have firstId, pick secondId using weighted frequencies
        if (firstId != null && secondId == null) {
            secondId = pickWeightedSecondGivenFirst(firstId);
        }

        List<Integer> startIds;
        if (firstId == null || secondId == null) {
            startIds = pickStartFromTrigramFollowers();
        } else {
            startIds = List.of(firstId, secondId);
        }

        if (startIds.size() < 2) return "";
        return buildSentence(startIds, maxTokens, stopWord);
    }


    // ---------- internals ----------
    private String buildSentence(List<Integer> seed,
                             int maxTokens,
                             String stopWordRaw) {
        if (seed.size() < 2) return "";

        final String stopWord =
                (stopWordRaw == null ? null : stopWordRaw.toLowerCase(Locale.ROOT));

        List<Integer> ids = new ArrayList<>(seed);

        // track used trigrams instead of every word
        Set<String> usedTrigrams = new HashSet<>();

        int secondLast = ids.get(ids.size() - 2);
        int last       = ids.get(ids.size() - 1);

        while (ids.size() < Math.max(2, maxTokens)) {
            long key = makePairKey(secondLast, last);
            List<int[]> cands = followers.get(key);
            if (cands == null || cands.isEmpty()) {
                // no trigram continuation for this pair
                break;
            }

            int nextId = -1;

            // try candidates in weighted fashion, but skip bad ones
            // (we'll just roll repeatedly until we find an acceptable one or give up)
            int attempts = 0;
            while (attempts < 10 && nextId == -1) {
                int candId = chooseWeightedNext(cands);

                // avoid trivial loops: x y y or x y x
                if (candId == last) {
                    attempts++;
                    continue;
                }
                if (candId == secondLast) {
                    attempts++;
                    continue;
                }

                String triSig = secondLast + "-" + last + "-" + candId;
                if (usedTrigrams.contains(triSig)) {
                    attempts++;
                    continue;
                }

                usedTrigrams.add(triSig);
                nextId = candId;
            }

            // if everything was filtered out, stop
            if (nextId == -1) break;

            ids.add(nextId);

            // slide window
            secondLast = last;
            last       = nextId;

            if (stopWord != null) {
                String w = idToWord.getOrDefault(last, "").toLowerCase(Locale.ROOT);
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
}
