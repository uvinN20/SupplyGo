package com.supplygo.service;

import java.util.ArrayList;
import java.util.List;
import com.supplygo.model.FruitBatch;
import com.supplygo.storage.InventoryRepository.State;

/** Calculations over one locked snapshot. Callers commit orders and stock together. */
final class StockOperations {
    private StockOperations() { }
    static long onHand(State state, String productId) {
        return state.batches().stream().filter(b -> b.productId().equals(productId)).mapToLong(FruitBatch::quantity).sum();
    }
    static long reserved(State state, String productId) {
        return state.orders().stream().filter(o -> o.productId().equals(productId) && o.status().reservesStock())
                .mapToLong(o -> o.quantity()).sum();
    }
    static long available(State state, String productId) { return onHand(state, productId) - reserved(state, productId); }
    static void requireProduct(State state, String id) throws AuthException {
        if (state.products().stream().noneMatch(p -> p.id().equals(id)))
            throw new AuthException("PRODUCT_NOT_FOUND", "Unknown product ID");
    }
    static List<FruitBatch> consumeFifo(State state, String productId, int quantity) {
        int remaining = quantity;
        var batches = new ArrayList<FruitBatch>();
        for (FruitBatch batch : state.batches()) {
            int taken = batch.productId().equals(productId) ? Math.min(remaining, batch.quantity()) : 0;
            remaining -= taken;
            if (taken < batch.quantity()) batches.add(new FruitBatch(batch.id(), batch.productId(), batch.quantity() - taken,
                    batch.receivedAt(), batch.receivedBy()));
        }
        if (remaining != 0) throw new IllegalStateException("Insufficient physical stock");
        return batches;
    }
}
