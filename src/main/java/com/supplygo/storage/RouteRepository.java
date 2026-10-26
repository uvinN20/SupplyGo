package com.supplygo.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.supplygo.model.RouteGraph;

/** One server owns routes.json. Memory changes only after successful atomic replacement. */
public final class RouteRepository implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final Path file; private final FileChannel channel; private final FileLock lock;
    private RouteGraph state = new RouteGraph(1, List.of(), List.of()); private boolean closed;
    public RouteRepository(Path directory) throws IOException {
        Files.createDirectories(directory); file = directory.resolve("routes.json");
        channel = FileChannel.open(directory.resolve("routes.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock(); if (lock == null) throw new IOException("Route store already in use");
            if (Files.exists(file)) {
                if (Files.size(file) > 1024 * 1024) throw new IOException("Route file exceeds 1 MiB");
                JsonObject data = com.supplygo.protocol.ProtocolCodec.parse(Files.readString(file));
                if (data == null || !data.keySet().equals(Set.of("version", "locations", "roads"))) throw new IOException("Invalid route schema");
                for (JsonElement row : data.getAsJsonArray("locations")) if (!row.getAsJsonObject().keySet().equals(Set.of("id", "name"))) throw new IOException("Invalid location fields");
                for (JsonElement row : data.getAsJsonArray("roads")) if (!row.getAsJsonObject().keySet().equals(Set.of("from", "to", "meters"))) throw new IOException("Invalid road fields");
                if (!(data.get("version") instanceof JsonPrimitive version) || !version.isNumber() || !version.getAsString().equals("1")) throw new IOException("Unsupported route schema version");
                for (JsonElement element : data.getAsJsonArray("locations")) {
                    JsonObject row = element.getAsJsonObject();
                    for (String key : List.of("id", "name")) if (!(row.get(key) instanceof JsonPrimitive value) || !value.isString()) throw new IOException("Invalid location value");
                }
                for (JsonElement element : data.getAsJsonArray("roads")) {
                    JsonObject row = element.getAsJsonObject();
                    for (String key : List.of("from", "to")) if (!(row.get(key) instanceof JsonPrimitive value) || !value.isString()) throw new IOException("Invalid road endpoint");
                    if (!(row.get("meters") instanceof JsonPrimitive value) || !value.isNumber() || !value.getAsString().matches("[1-9][0-9]{0,9}")) throw new IOException("Invalid road distance");
                }
                state = JSON.fromJson(data, RouteGraph.class);
            }
        } catch (IOException | RuntimeException error) { channel.close(); throw new IOException("Cannot open route store", error); }
    }
    public synchronized RouteGraph snapshot() { if (closed) throw new IllegalStateException("Route store closed"); return state; }
    public synchronized void save(RouteGraph next) throws IOException {
        if (closed) throw new IOException("Route store closed");
        byte[] bytes = JSON.toJson(next).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 1024 * 1024) throw new IOException("Route file exceeds 1 MiB");
        Path temporary = Files.createTempFile(file.getParent(), "routes-", ".tmp"); boolean committed = false;
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) output.write(buffer); output.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); state = next; committed = true;
        } finally { if (!committed) Files.deleteIfExists(temporary); }
    }
    @Override public synchronized void close() throws IOException { if (!closed) { closed = true; try { lock.release(); } finally { channel.close(); } } }
}
