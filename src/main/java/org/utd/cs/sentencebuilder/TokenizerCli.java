/**
 * TokenizerCli.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Kevin Tran
 * Date: October 3 2025
 *
 * Description:
 *  Simple CLI for testing the Tokenizer.
 *  Reads a text file, prints top words and top bigrams.
 */


package org.utd.cs.sentencebuilder;

import java.util.Map;

public class TokenizerCli {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "data/sample.txt";
        System.out.println("Reading: " + path);

        var res = Tokenizer.processFile(path);

        System.out.println("\nTotal tokens: " + res.tokens.size());
        System.out.println("Unique words : " + res.unigrams.size());

        System.out.println("\nTop 10 words:");
        for (var e : Tokenizer.topK(res.unigrams, 10)) {
            System.out.println(e.getKey() + " : " + e.getValue());
        }

        String probe = "the";
        System.out.println("\nTop followers of '" + probe + "':");
        Map<String,Integer> row = res.bigrams.getOrDefault(probe, Map.of());
        row.entrySet().stream()
           .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
           .limit(10)
           .forEach(e -> System.out.println(probe + " -> " + e.getKey() + " : " + e.getValue()));
    }
}
