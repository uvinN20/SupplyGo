package com.supplygo.algorithm;

/** One comparable offer for the same product, currency and whole-unit basis. */
public record SupplierCandidate(String username, int freshnessDaysRemaining,
        long unitPriceMinor, int distanceMeters) {
    public SupplierCandidate {
        if (username == null || !username.matches("[a-z][a-z0-9_]{2,31}"))
            throw new IllegalArgumentException("Invalid supplier username");
        if (freshnessDaysRemaining < 0 || freshnessDaysRemaining > 3650
                || unitPriceMinor < 1 || unitPriceMinor > 1_000_000_000_000L
                || distanceMeters < 0 || distanceMeters > 1_000_000_000)
            throw new IllegalArgumentException("Invalid freshness, price or distance");
    }
}
