package com.supplygo.model;

import java.time.Instant;
import java.util.UUID;

/** One product per order; identity and timestamps are assigned by the server. */
public record SupplyOrder(long id, String clientOrderId, String sellerUsername, String productId,
        int quantity, OrderStatus status, String createdAt, String updatedAt, String updatedBy) {
    public SupplyOrder {
        if (id < 1 || !validClientOrderId(clientOrderId) || !validUsername(sellerUsername)
                || productId == null || !productId.matches("[A-Z0-9_-]{1,24}")
                || quantity < 1 || quantity > 1_000_000 || status == null || !validUsername(updatedBy))
            throw new IllegalArgumentException("Invalid order");
        Instant created = Instant.parse(createdAt);
        Instant updated = Instant.parse(updatedAt);
        if (updated.isBefore(created)) throw new IllegalArgumentException("Invalid order timestamps");
    }
    public static boolean validClientOrderId(String value) {
        if (value == null || value.length() != 36) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException exception) { return false; }
    }
    private static boolean validUsername(String value) {
        return value != null && value.matches("[a-z][a-z0-9_]{2,31}");
    }
}
