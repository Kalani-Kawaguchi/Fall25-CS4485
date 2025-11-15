/**
 *  Author: Kevin Tran
 *  CS 4485 Fall 2025
 *  October 18 2025
 * 
 *  Description:
 *  Interface defining the contract for all sentence generator strategies.
 *
 *  Each implementation must:
 *  - Load necessary data from the database
 *  - Generate sentences either randomly or from given starting words
 */

/**
 * TO USE:
 *  # compile
 *  mvn -q -DskipTests compile
 *
 *  # run (no seed, defaults)
 *  mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli
 *
 *  # run with a seed
 *  mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli \
 *   -Dexec.args="--seed=the --max=25"
 *
 *  # run with a stop word and length cap
 *  mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli \
 *   -Dexec.args="--max=30 --stop=oz"
 * 
 *  # run (no seed, weighted)
 *  mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli
 *   -Dexec.args="--algo=weighted"
 * 
 *  # run with a seed
 *  mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli \
 *   -Dexec.args="--algo=weighted --seed=the"
 * 
 *  # run with a stop word and length cap
 *  mvn -q -DskipTests exec:java -Dexec.mainClass=org.utd.cs.sentencebuilder.RunGeneratorCli \
 *   -Dexec.args="--algo=weighted --seed=the --max=25 --stop=oz
 * 
 */

package org.utd.cs.sentencebuilder;

import java.util.List;

public class RunGeneratorCli {

    public static void main(String[] args) {

        // ----- Defaults -----
        String algo = "greedy";        // greedy | weighted | bi_greedy | bi_weighted
        int max = 20;
        String stop = null;
        List<String> seed = List.of();

        // ----- Parse flags -----
        for (String a : args) {
            if (a.startsWith("--algo="))       algo = a.substring(7).toLowerCase();
            else if (a.startsWith("--max="))   max = Integer.parseInt(a.substring(6));
            else if (a.startsWith("--stop="))  stop = a.substring(7).toLowerCase();
            else if (a.startsWith("--seed="))  seed = List.of(a.substring(7).toLowerCase());
        }

        // ----- Pick the generator -----
        SentenceGenerator gen;

        switch (algo) {
            case "bi_weighted":
                gen = new BigramWeightedGenerator();
                break;
            case "bi_greedy":
                gen = new BigramGreedyGenerator();
                break;
            case "weighted":    // new Weighted Generation w/ Trigram       - Renz
                gen = new TrigramWeightedGenerator();
                break;
            case "greedy":      // new Greedy Generation w/ Trigram         - Renz
            default:
                gen = new TrigramGreedyGenerator();
                break;
        }

        gen.loadData(); // ensures DB fetch performed once

        // ----- Generate sentence -----
        String output = seed.isEmpty()
                ? gen.generateSentence(max, stop)
                : gen.generateSentence(seed, max, stop);

        // ----- Print output -----
        System.out.println("\n--- Generated (" + algo + ") ---");
        System.out.println(output);
        System.out.println("(tokens=" + output.split("\\s+").length + ", max=" + max + (stop != null ? ", stop=" + stop : "") + ", algo=" + algo + ")");

    }
}
