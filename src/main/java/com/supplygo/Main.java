package com.supplygo;

/** Entry point for the SupplyGo application during project setup. */
public final class Main {

    private Main() {
        // Utility class: no instances.
    }

    public static String banner() {
        return "SupplyGo - Java project initialized.";
    }

    public static void main(String[] args) {
        System.out.println(banner());
    }
}
