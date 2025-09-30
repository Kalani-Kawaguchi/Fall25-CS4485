package org.utd.cs.sentencebuilder;

public class Main {
    public static void main(String[] args) {
        DatabaseManager dbManager = new DatabaseManager();
        System.out.println("--- Building Database ---");
        dbManager.buildDatabase();
        System.out.println();
    }
}