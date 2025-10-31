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
 */

package org.utd.cs.sentencebuilder;

import java.util.List;

public class RunGeneratorCli {
    public static void main(String[] args) {
        // Options you can tweak
        int max = 20;                       // default cap
        String stop = null;                 // e.g. "oz" to stop when "oz" appears
        List<String> seed = List.of();      // e.g. List.of("the")

        // Parse simple flags: --max=25 --stop=oz --seed=the
        for (String a : args) {
            if (a.startsWith("--max="))  max = Integer.parseInt(a.substring(6));
            else if (a.startsWith("--stop=")) stop = a.substring(7).toLowerCase();
            else if (a.startsWith("--seed=")) seed = List.of(a.substring(7).toLowerCase());
        }

        BigramGreedyGenerator gen = new BigramGreedyGenerator();
        gen.loadData(); // optional, generate* will do it if not called

        String s = seed.isEmpty()
                ? gen.generateSentence(max, stop)
                : gen.generateSentence(seed, max, stop);
        System.out.println("\n--- Generated ---");
        System.out.println(s);
        System.out.println("(len=" + s.split("\\s+").length + ", max=" + max +
                        (stop != null ? ", stop=" + stop : "") + ")");

    }
}
