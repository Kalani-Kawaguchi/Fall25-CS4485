/**
 *  Author: Kevin Tran
 *  CS 4485 Fall 2025
 *  October 18 2025
 * 
 *  Description:
 *  Command-line entry point for testing different sentence generator strategies.
 *
 *  This class:
 *    - Parses simple CLI flags (algo, seed word, max length, stop word)
 *    - Instantiates the requested SentenceGenerator via GeneratorFactory
 *    - Prints a single generated sentence and some basic stats
 *
 *  Supported flags:
 *    --algo=bi-greedy     Use greedy bigram generator
 *    --algo=bi-weighted   Use weighted-random bigram generator
 *    --algo=tri-greedy    Use greedy trigram generator (default)
 *    --algo=tri-weighted  Use weighted-random trigram generator
 *    --seed=word          Optional starting word (e.g., "the")
 *    --max=N              Maximum number of tokens (default: 20)
 *    --stop=word          Optional stop word; generation stops once this appears
 *
 *  TO USE (PowerShell):
 *
 *    # Compile
 *    mvn -q -DskipTests compile
 *
 *    # Run with defaults (greedy, no seed, max=20)
 *    mvn -q -DskipTests exec:java `
 *      "-Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli"
 *
 *    # Run greedy with a seed and custom max length
 *    mvn -q -DskipTests exec:java `
 *      "-Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli" `
 *      "-Dexec.args=--algo=greedy --seed=the --max=25"
 *
 *    # Run weighted random with a stop word
 *    mvn -q -DskipTests exec:java `
 *      "-Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli" `
 *      "-Dexec.args=--algo=weighted --max=30 --stop=oz"
 *
 */

package org.utd.cs.sentencebuilder;

import java.util.List;

public class RunGeneratorCli {
    public static void main(String[] args) {
        int max = 1000;
        String stop = null;
        List<String> seed = List.of();
        String algo = "greedy";

        for (String a : args) {
            if (a.startsWith("--max=")) {
                max = Integer.parseInt(a.substring(6));
            } else if (a.startsWith("--stop=")) {
                stop = a.substring(7).toLowerCase();
            } else if (a.startsWith("--seed=")) {
                seed = List.of(a.substring(7).toLowerCase());
            } else if (a.startsWith("--algo=")) {
                algo = a.substring(7).toLowerCase();
            }
        }

        try {
            DatabaseManager db = new DatabaseManager();
            GeneratorDataController data = new GeneratorDataController(db);

            SentenceGenerator gen = GeneratorFactory.create(algo, data);
            System.out.println("Using algorithm: " + gen.getName());

            String s = seed.isEmpty()
                    ? gen.generateSentence(max, stop)
                    : gen.generateSentence(seed, max, stop);

            System.out.println("\n--- Generated ---");
            System.out.println(s);
            System.out.println("(len=" + s.split("\\s+").length +
                    ", max=" + max +
                    (stop != null ? ", stop=" + stop : "") +
                    ", algo=" + algo + ")");

            DatabaseManager.closeDataSource();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
