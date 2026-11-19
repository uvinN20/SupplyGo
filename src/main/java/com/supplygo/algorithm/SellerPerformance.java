package com.supplygo.algorithm;

/** Whole kilograms and number of fulfilled weight-based orders. */
public record SellerPerformance(String username, long fulfilledUnits, int fulfilledOrders) {
    public SellerPerformance {
        if (username == null || !username.matches("[a-z][a-z0-9_]{2,31}")
                || fulfilledUnits < 0 || fulfilledOrders < 0
                || (fulfilledOrders == 0) != (fulfilledUnits == 0)
                || fulfilledUnits < fulfilledOrders)
            throw new IllegalArgumentException("Invalid seller performance");
    }
}
