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
    public record State(int version, long nextBatchId, List<Product> products, List<FruitBatch> batches) {
        public State {
            products = List.copyOf(products); batches = List.copyOf(batches);
            if (version != 1 || nextBatchId < 1 || products.size() > 1000 || batches.size() > 10000)
                throw new IllegalArgumentException("Invalid inventory state");
            Set<String> ids = new HashSet<>();
            for (Product product : products) if (!ids.add(product.id())) throw new IllegalArgumentException("Duplicate product");
            long previous = 0;
            for (FruitBatch batch : batches) {
                if (!ids.contains(batch.productId()) || batch.id() <= previous || batch.id() >= nextBatchId)
                    throw new IllegalArgumentException("Invalid batch order or product reference");
                previous = batch.id();
            }
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
                state = JSON.fromJson(Files.readString(file), State.class);
                if (state == null) throw new IOException("Empty inventory store");
            } else state = new State(1, 1, List.of(), List.of());
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw new IOException("Cannot open inventory store: " + exception.getMessage(), exception);
        }
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
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            state = next;
        } finally { Files.deleteIfExists(temporary); }
    }
    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; try { lock.release(); } finally { channel.close(); } }
    }
}
