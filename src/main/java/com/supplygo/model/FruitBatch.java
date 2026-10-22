package com.supplygo.model;

import java.time.Instant;

/** Receipt order is represented by a monotonic server-assigned batch ID. */
public record FruitBatch(long id, String productId, int quantity, String receivedAt, String receivedBy) {
    public FruitBatch {
        if (id < 1 || productId == null || quantity < 1 || quantity > 1_000_000
                || receivedBy == null || !receivedBy.matches("[a-z][a-z0-9_]{2,31}"))
            throw new IllegalArgumentException("Invalid fruit batch");
        Instant.parse(receivedAt);
    }
}
