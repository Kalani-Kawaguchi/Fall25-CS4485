package org.utd.cs.sentencebuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BigramGreedyUsingEOSPredictor implements SentenceGenerator {

    private static final Logger logger = LoggerFactory.getLogger(BigramGreedyUsingEOSPredictor.class);
    private static final int DEFAULT_MAX_TOKENS = 100;
    private static final double EOS_THRESHOLD = 0.5;

    private final GeneratorDataController dataController;
    private final EosPredictor eosPredictor;

    // We cache these for convenience, though we could pull from controller every time
    private final Map<String, Integer> wordToId;
    private final Map<Integer, String> idToWord;
    private final Map<Integer, List<int[]>> followers;
    private final List<int[]> startCandidates;

    public BigramGreedyUsingEOSPredictor(GeneratorDataController dataController,
                                         EosPredictor eosPredictor) {
        this.dataController = dataController;
        this.eosPredictor = eosPredictor;

        // Unpack frequently used data for readability
        this.wordToId = dataController.getWordToId();
        this.idToWord = dataController.getIdToWord();
        this.followers = dataController.getBigramFollowers();
        this.startCandidates = dataController.getStartCandidates();
    }

    @Override
    public String getName() {
        return "BigramGreedy + EOS Predictor";
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
        // We ignore stopWord as requested, relying on EOS predictor
        Integer startId = pickStartId();
        if (startId == null) return "";
        return buildGreedySentence(List.of(startId), maxTokens);
    }

    @Override
    public String generateSentence(List<String> startingWords, int maxTokens, String stopWord) {
        // We ignore stopWord as requested
        List<Integer> startIds = dataController.resolveSeedWords(startingWords, 1);

        Integer startId;
        if (startIds.isEmpty()) {
            startId = pickStartId();
        } else {
            startId = startIds.get(0);
        }

        if (startId == null) return "";
        return buildGreedySentence(List.of(startId), maxTokens);
    }

    /**
     * ID-based entry point used by the controller/CLI.
     */
    public String generateFromIds(List<Integer> startingIds, int maxTokens) {
        Integer startId = null;

        if (startingIds != null && !startingIds.isEmpty()) {
            startId = startingIds.get(0);
        }
        if (startId == null) {
            startId = pickStartId();
        }
        if (startId == null) return "";

        return buildGreedySentence(List.of(startId), maxTokens);
    }

    // ---------- internals ----------

    private Integer pickStartId() {
        if (!startCandidates.isEmpty()) return startCandidates.get(0)[0];
        return idToWord.isEmpty() ? null : idToWord.keySet().iterator().next();
    }

    private String buildGreedySentence(List<Integer> seed, int maxTokens) {
        List<Integer> ids = new ArrayList<>(seed);
        int curr = ids.get(ids.size() - 1);
        Integer last = null;

        // Tracks edges (wordA -> wordB) to prevent cycling in deterministic greedy path
        Set<Long> usedPairs = new HashSet<>();

        // 1. Loop until Max Tokens
        while (ids.size() < Math.max(1, maxTokens)) {

            List<int[]> cands = followers.get(curr);
            if (cands == null || cands.isEmpty()) break;

            int nextId = -1;

            for (int[] cand : cands) {
                int candId = cand[0];

                if (last != null && candId == last) continue; // avoid A→B→A

                // Pack edge into Long for loop detection: curr -> candId
                long pairKey = (((long) curr) << 32) | (candId & 0xffffffffL);
                if (usedPairs.contains(pairKey)) continue;

                nextId = candId;
                usedPairs.add(pairKey);
                break; // Greedy: take the first valid one
            }

            if (nextId == -1) break; // Dead end or all paths used
            if (nextId == curr) break; // Prevent self-loop

            ids.add(nextId);
            last = curr;
            curr = nextId;
            // 3. EOS Check: Ask the predictor if the current sequence looks like a complete sentence
            double probability = eosPredictor.predictEosProbability(ids);

            if (probability >= EOS_THRESHOLD) {
                logger.debug("Stopping early. Length: {} | Prob: {}", ids.size(), probability);
                break;
            }
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
}