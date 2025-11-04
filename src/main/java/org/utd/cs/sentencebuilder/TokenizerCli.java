/**
 * TokenizerCli.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kevin Tran
 * Date: October 3 2025
 *
 * Description:
 *  Simple CLI for testing the Tokenizer (no DB).
 *  - Reads a .txt file (default: data/sample.txt)
 *  - Prints top unigrams (using Word objects)
 *  - Prints top followers of a probe word from bigramCounts
 */

 package org.utd.cs.sentencebuilder;

import java.util.Map;
import java.util.List;
import java.util.Map.Entry;

public class TokenizerCli {
    public static void main(String[] args) throws Exception {
        String path = (args.length > 0) ? args[0] : "data/sample.txt";
        System.out.println("Reading: " + path);

        Tokenizer.Result res = Tokenizer.processFile(path);

        System.out.println("\nTokens: " + res.tokens.size());
        System.out.println("Unique words (Word objects): " + res.words.size());

        Map<String,Integer> uniCount = Tokenizer.toUnigramCountMap(res.words);

        System.out.println("\nTop 10 words:");
        List<Entry<String,Integer>> top = Tokenizer.topK(uniCount, 10);
        for (Entry<String,Integer> e : top) {
            System.out.println(e.getKey() + " : " + e.getValue());
        }
        //to lazy to fix -vincent
        /**
        String probe = "the";
        System.out.println("\nTop followers of '" + probe + "':");
        Map<String,Integer> row = res.bigramCounts.getOrDefault(probe, Map.of());
        row.entrySet().stream()
           .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
           .limit(10)
           .forEach(e -> System.out.println(probe + " -> " + e.getKey() + " : " + e.getValue()));
        **/

        // Show one Word object to confirm fields are set
        res.words.entrySet().stream().limit(1).forEach(e -> {
            Word w = e.getValue();
            System.out.println("\nSample Word object:");
            System.out.println("value=" + w.getWordValue()
                    + ", total=" + w.getTotalOccurrences()
                    + ", start=" + w.getStartSentenceCount()
                    + ", end=" + w.getEndSequenceCount());
        });
    }
}
