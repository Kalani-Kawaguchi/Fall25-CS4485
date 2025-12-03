/**
 *  Author: Kevin Tran
 *  CS4485. Fall 2025
 *  October 18 2025
 *  
 *  Description:
 *  Implements a greedy bigram sentence generator. At each step, selects
 *  the most frequent follower word unless prevented by loop-avoidance rules.
 *
 *  Uses shared cached data from GeneratorDataController:
 *    - Word/ID mappings
 *    - Bigram follower lists
 *    - Sentence-start candidates
 *
 *  Supports:
 *    - Optional seed words
 *    - Optional stop word
 *    - Maximum token limits
 *    - Loop prevention (no A→B→A, no repeated bigrams)
 */


package org.utd.cs.sentencebuilder;

import java.util.*;

/**
 * Greedy bigram generator:
 * - always picks the most frequent follower (with some loop guards)
 */
public class BigramGreedyGenerator implements SentenceGenerator {

    private static final int DEFAULT_MAX_TOKENS = 1000;

    private final Map<String, Integer> wordToId;
    private final Map<Integer, String> idToWord;
    private final Map<Integer, List<int[]>> followers;
    private final List<int[]> startCandidates;

    private final EosPredictor eosPredictor;
    private static final double EOS_THRESHOLD = 0.5;

    public BigramGreedyGenerator(Map<String, Integer> wordToId,
                                 Map<Integer, String> idToWord,
                                 Map<Integer, List<int[]>> followers,
                                 List<int[]> startCandidates,
                                 EosPredictor eosPredictor) {
        this.wordToId = wordToId;
        this.idToWord = idToWord;
        this.followers = followers;
        this.startCandidates = startCandidates;
        this.eosPredictor = eosPredictor;
    }

    @Override
    public String getName() {
        return "BigramGreedyGenerator";
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
        Integer startId = pickStartId();
        if (startId == null) return "";
        return buildGreedySentence(List.of(startId), maxTokens, stopWord);
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
        if (startId == null) startId = pickStartId();
        if (startId == null) return "";
        return buildGreedySentence(List.of(startId), maxTokens, stopWord);
    }

    /**
     * ID-based entry point used by the controller/CLI.
     * startingIds: first element (if present) is used as the start;
     *              otherwise we fall back to pickStartId().
     */
    public String generateFromIds(List<Integer> startingIds,
                                  int maxTokens,
                                  String stopWord) {
        Integer startId = null;

        if (startingIds != null && !startingIds.isEmpty()) {
            startId = startingIds.get(0);
        }
        if (startId == null) {
            startId = pickStartId();
        }
        if (startId == null) return "";

        return buildGreedySentence(List.of(startId), maxTokens, stopWord);
    }

    // ---------- internals ----------

    private Integer pickStartId() {
        if (!startCandidates.isEmpty()) return startCandidates.get(0)[0];
        return idToWord.isEmpty() ? null : idToWord.keySet().iterator().next();
    }

    private String buildGreedySentence(List<Integer> seed, int maxTokens, String stopWordRaw) {
//        final String stopWord = (stopWordRaw == null) ? null : stopWordRaw.toLowerCase(Locale.ROOT);

        List<Integer> ids = new ArrayList<>(seed);
//        Set<Integer> visited = new HashSet<>(ids);
        int curr = ids.get(ids.size() - 1);
        Integer last = null;
        Set<Long> usedPairs = new HashSet<>();

        while (ids.size() < Math.max(1, maxTokens)) {
            List<int[]> cands = followers.get(curr);
            if (cands == null || cands.isEmpty()) break;

            int nextId = -1;
            for (int[] cand : cands) {
                int candId = cand[0];

                if (last != null && candId == last) continue; // avoid A→B→A
                long pairKey = (((long) curr) << 32) | (candId & 0xffffffffL);
                if (usedPairs.contains(pairKey)) continue;

                nextId = candId;
                usedPairs.add(pairKey);
                break;
            }

            if (nextId == -1) break;
            if (nextId == curr) break;

            ids.add(nextId);
//            visited.add(nextId);
            last = curr;
            curr = nextId;

            double eosProb = eosPredictor.predictEosProbability(ids);
            if (eosProb >= EOS_THRESHOLD) {
                break;
            }

//            if (stopWord != null) {
//                String w = idToWord.getOrDefault(curr, "").toLowerCase(Locale.ROOT);
//                if (w.equals(stopWord)) break;
//            }
        }

        return render(ids);
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

//    public String generateSentenceWithStop(String stopWord) {
//        return generateSentence(DEFAULT_MAX_TOKENS, stopWord);
//    }
}
