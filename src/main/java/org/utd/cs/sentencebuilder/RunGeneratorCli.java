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

package org.utd.cs.sentencebuilder;

import java.util.Arrays;
import java.util.List;

public class RunGeneratorCli {
    public static void main(String[] args) {
        SentenceGenerator gen = new BigramGreedyGenerator();

        String output;
        if (args.length == 0) {
            output = gen.generateSentence(); // auto-pick start
        } else {
            List<String> start = Arrays.asList(args[0].split("\\s+"));
            output = gen.generateSentence(start);
        }

        System.out.println(output);
        DatabaseManager.closeDataSource();
    }
}
