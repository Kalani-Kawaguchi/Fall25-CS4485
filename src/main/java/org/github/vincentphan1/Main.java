package org.github.vincentphan1;

import org.github.vincentphan1.DatabaseManager;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        DatabaseManager dbManager = new DatabaseManager();
        System.out.println("--- Building Database ---");
        dbManager.buildDatabase();
        System.out.println();
    }
}