package com.supplygo.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.supplygo.model.*;

/** One server owns the store. Publish memory only after an atomic file replacement succeeds. */
public final class InventoryRepository implements AutoCloseable {
    /** Orders and stock share one immutable snapshot and one atomic disk commit. */
    public record State(int version, long nextBatchId, List<Product> products, List<FruitBatch> batches,
            long nextOrderId, List<SupplyOrder> orders) {
        public State {
            products = List.copyOf(products); batches = List.copyOf(batches); orders = List.copyOf(orders);
            if (version != 2 || nextBatchId < 1 || nextOrderId < 1 || products.size() > 1000
                    || batches.size() > 10000 || orders.size() > 10000)
                throw new IllegalArgumentException("Invalid inventory/order state");
            Set<String> ids = new HashSet<>();
            for (Product product : products) if (!ids.add(product.id())) throw new IllegalArgumentException("Duplicate product");
            Map<String, Long> onHand = new HashMap<>();
            long previous = 0;
            for (FruitBatch batch : batches) {
                if (!ids.contains(batch.productId()) || batch.id() <= previous || batch.id() >= nextBatchId)
                    throw new IllegalArgumentException("Invalid batch order or product reference");
                previous = batch.id();
                onHand.merge(batch.productId(), (long) batch.quantity(), Long::sum);
            }
            previous = 0;
            Set<String> submissions = new HashSet<>();
            Map<String, Long> reserved = new HashMap<>();
            for (SupplyOrder order : orders) {
                if (!ids.contains(order.productId()) || order.id() <= previous || order.id() >= nextOrderId
                        || !submissions.add(order.sellerUsername() + ":" + order.clientOrderId()))
                    throw new IllegalArgumentException("Invalid order ID, submission or product reference");
                previous = order.id();
                if (order.status().reservesStock()) reserved.merge(order.productId(), (long) order.quantity(), Long::sum);
            }
            for (var entry : reserved.entrySet()) {
                if (entry.getValue() > onHand.getOrDefault(entry.getKey(), 0L))
                    throw new IllegalArgumentException("Orders reserve more stock than is on hand");
            }
        }
        public State withStock(long nextBatch, List<Product> catalog, List<FruitBatch> stock) {
            return new State(2, nextBatch, catalog, stock, nextOrderId, orders);
        }
        public State withOrders(long nextOrder, List<SupplyOrder> updatedOrders, List<FruitBatch> stock) {
            return new State(2, nextBatchId, products, stock, nextOrder, updatedOrders);
        }
    }
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;
    private State state;
    private boolean closed;

    public InventoryRepository(Path directory) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("inventory.json");
        channel = FileChannel.open(directory.resolve("inventory.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("Inventory store is already in use");
            if (Files.exists(file)) {
                if (Files.size(file) > 8 * 1024 * 1024) throw new IOException("Inventory store exceeds size limit");
                state = readState(Files.readString(file));
                if (state == null) throw new IOException("Empty inventory store");
            } else state = new State(2, 1, List.of(), List.of(), 1, List.of());
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw new IOException("Cannot open inventory store: " + exception.getMessage(), exception);
        }
    }
    private static State readState(String source) {
        JsonObject json = JSON.fromJson(source, JsonObject.class);
        if (json == null || !(json.get("version") instanceof JsonPrimitive version) || !version.isNumber())
            throw new IllegalArgumentException("Missing store version");
        if (version.getAsString().equals("1")) {
            if (!json.keySet().equals(Set.of("version", "nextBatchId", "products", "batches")))
                throw new IllegalArgumentException("Invalid legacy inventory fields");
            // Upgrade in memory; the first successful mutation saves version 2 atomically.
            json.addProperty("version", 2);
            json.addProperty("nextOrderId", 1);
            json.add("orders", new JsonArray());
        }
        if (!json.keySet().equals(Set.of("version", "nextBatchId", "products", "batches", "nextOrderId", "orders")))
            throw new IllegalArgumentException("Invalid inventory/order fields");
        return JSON.fromJson(json, State.class);
    }
    public synchronized State snapshot() {
        if (closed) throw new IllegalStateException("Inventory store is closed");
        return state;
    }
    public synchronized void save(State next) throws IOException {
        if (closed) throw new IOException("Inventory store is closed");
        byte[] bytes = JSON.toJson(next).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 8 * 1024 * 1024) throw new IOException("Inventory store exceeds size limit");
        Path temporary = Files.createTempFile(file.getParent(), "inventory-", ".tmp");
        boolean committed = false;
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            state = next;
            committed = true;
        } finally { if (!committed) Files.deleteIfExists(temporary); }
    }
    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; try { lock.release(); } finally { channel.close(); } }
    }
}
