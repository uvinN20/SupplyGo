package com.supplygo.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.supplygo.model.BusinessProfile;
import com.supplygo.protocol.BusinessProfileData;

/** Synchronized snapshots and atomic replacement; one server process owns the file. */
public final class BusinessProfileRepository implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;
    private Map<String, BusinessProfile> profiles = Map.of();
    private boolean closed;
    public BusinessProfileRepository(Path directory) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("business-profiles.json");
        channel = FileChannel.open(directory.resolve("business-profiles.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("Business profiles are already in use");
            if (Files.exists(file)) {
                if (Files.size(file) > 8 * 1024 * 1024) throw new IOException("Profile store exceeds size limit");
                JsonObject data = JSON.fromJson(Files.readString(file), JsonObject.class);
                if (data == null || !data.keySet().equals(Set.of("version", "profiles"))
                        || !data.get("version").equals(new JsonPrimitive(1)) || !data.get("profiles").isJsonArray())
                    throw new IOException("Invalid profile store schema");
                Map<String, BusinessProfile> loaded = new TreeMap<>();
                for (JsonElement row : data.getAsJsonArray("profiles")) {
                    BusinessProfile profile = BusinessProfileData.decode(row);
                    if (loaded.putIfAbsent(profile.username(), profile) != null) throw new IOException("Duplicate profile username");
                }
                if (loaded.size() > 10000) throw new IOException("Too many profiles");
                profiles = Map.copyOf(loaded);
            }
        } catch (IOException | RuntimeException exception) {
            channel.close(); throw new IOException("Cannot open business profiles: " + exception.getMessage(), exception);
        }
    }
    public synchronized Map<String, BusinessProfile> snapshot() {
        if (closed) throw new IllegalStateException("Profile store is closed");
        return profiles;
    }
    public synchronized void save(BusinessProfile profile) throws IOException {
        if (closed) throw new IOException("Profile store is closed");
        Map<String, BusinessProfile> next = new TreeMap<>(profiles); next.put(profile.username(), profile);
        if (next.size() > 10000) throw new IOException("Profile limit reached");
        JsonObject data = new JsonObject(); data.addProperty("version", 1);
        JsonArray rows = new JsonArray(); next.values().forEach(p -> rows.add(BusinessProfileData.encode(p))); data.add("profiles", rows);
        byte[] bytes = JSON.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 8 * 1024 * 1024) throw new IOException("Profile store exceeds size limit");
        Map<String, BusinessProfile> committedState = Map.copyOf(next);
        Path temporary = Files.createTempFile(file.getParent(), "profiles-", ".tmp");
        boolean committed = false;
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) output.write(buffer); output.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            profiles = committedState; committed = true;
        } finally { if (!committed) Files.deleteIfExists(temporary); }
    }
    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; try { lock.release(); } finally { channel.close(); } }
    }
}
