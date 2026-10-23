package com.supplygo.service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import com.supplygo.model.*;
import com.supplygo.storage.InventoryRepository;
import com.supplygo.storage.InventoryRepository.State;

/** Shares the repository monitor with orders: validation, allocation and save are one transaction. */
public final class InventoryService {
    public static final int PAGE_SIZE = 10;
    private final InventoryRepository repository;
    public InventoryService(InventoryRepository repository) { this.repository = Objects.requireNonNull(repository); }

    public void createProduct(UserProfile actor, String id, String name) throws AuthException {
        synchronized (repository) {
            writer(actor);
            Product product;
            try { product = new Product(id, name); }
            catch (IllegalArgumentException exception) { throw new AuthException("INVALID_INVENTORY", exception.getMessage()); }
            State state = repository.snapshot();
            if (state.products().stream().anyMatch(p -> p.id().equals(id))) throw new AuthException("PRODUCT_EXISTS", "Product ID already exists");
            if (state.products().size() == 1000) throw new AuthException("INVENTORY_LIMIT", "Product limit reached");
            var products = new ArrayList<>(state.products()); products.add(product);
            save(state.withStock(state.nextBatchId(), products, state.batches()));
        }
    }
    public void receive(UserProfile actor, String productId, int quantity) throws AuthException {
        synchronized (repository) {
            writer(actor); quantity(quantity);
            State state = repository.snapshot(); StockOperations.requireProduct(state, productId);
            if (state.batches().size() == 10000 || state.nextBatchId() == Long.MAX_VALUE)
                throw new AuthException("INVENTORY_LIMIT", "Batch limit reached");
            var batches = new ArrayList<>(state.batches());
            batches.add(new FruitBatch(state.nextBatchId(), productId, quantity, Instant.now().toString(), actor.username()));
            save(state.withStock(state.nextBatchId() + 1, state.products(), batches));
        }
    }
    public void withdraw(UserProfile actor, String productId, int quantity) throws AuthException {
        synchronized (repository) {
            writer(actor); quantity(quantity);
            State state = repository.snapshot(); StockOperations.requireProduct(state, productId);
            if (StockOperations.available(state, productId) < quantity)
                throw new AuthException("INSUFFICIENT_STOCK", "Not enough unreserved stock; no stock was changed");
            save(state.withStock(state.nextBatchId(), state.products(), StockOperations.consumeFifo(state, productId, quantity)));
        }
    }
    public JsonObject list(UserProfile actor, boolean batches, int offset) throws AuthException {
        synchronized (repository) {
            if (actor == null) throw new AuthException("AUTH_REQUIRED", "Sign in first");
            if (offset < 0 || offset > 10000) throw new AuthException("INVALID_INVENTORY", "Invalid page offset");
            State state = repository.snapshot();
            JsonArray rows = new JsonArray();
            int size = batches ? state.batches().size() : state.products().size();
            int end = Math.min(size, offset + PAGE_SIZE);
            for (int i = offset; i < end; i++) {
                JsonObject row = new JsonObject();
                if (batches) {
                    FruitBatch b = state.batches().get(i);
                    row.addProperty("id", b.id()); row.addProperty("productId", b.productId()); row.addProperty("quantity", b.quantity());
                    row.addProperty("receivedAt", b.receivedAt()); row.addProperty("receivedBy", b.receivedBy());
                } else {
                    Product p = state.products().get(i);
                    row.addProperty("id", p.id()); row.addProperty("name", p.name());
                    row.addProperty("quantity", StockOperations.available(state, p.id()));
                    row.addProperty("onHand", StockOperations.onHand(state, p.id()));
                    row.addProperty("reserved", StockOperations.reserved(state, p.id()));
                }
                rows.add(row);
            }
            JsonObject result = new JsonObject(); result.add(batches ? "batches" : "products", rows);
            result.addProperty("nextOffset", end < size ? end : -1);
            return result;
        }
    }
    private static void writer(UserProfile actor) throws AuthException {
        if (actor == null) throw new AuthException("AUTH_REQUIRED", "Sign in first");
        if (actor.role() != Role.ADMIN && actor.role() != Role.PRODUCTION_MANAGER)
            throw new AuthException("FORBIDDEN", "Only administrators and production managers can change inventory");
    }
    private static void quantity(int quantity) throws AuthException {
        if (quantity < 1 || quantity > 1_000_000) throw new AuthException("INVALID_INVENTORY", "Quantity must be 1–1000000 whole fruit units");
    }
    private void save(State state) throws AuthException {
        try { repository.save(state); }
        catch (IOException exception) { throw new AuthException("STORAGE_ERROR", "Could not save inventory; no stock was changed"); }
    }
}
