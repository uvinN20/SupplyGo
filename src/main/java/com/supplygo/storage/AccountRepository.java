package com.supplygo.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import com.supplygo.security.PasswordHasher;

/** Server-owned account file. One process holds its lock; updates replace the file atomically. */
public final class AccountRepository implements AutoCloseable {
    public static final int MAX_ACCOUNTS = 10_000;
    private static final long MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    public record Account(UserProfile profile, PasswordHasher.Hash passwordHash) { }
    private final Path directory;
    private final Path file;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private Map<String, Account> accounts = new LinkedHashMap<>();
    private boolean closed;

    public AccountRepository(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath();
        Files.createDirectories(this.directory);
        restrict(this.directory, true);
        file = this.directory.resolve("users.json");
        Path lockFile = this.directory.resolve("users.lock");
        lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            restrict(lockFile, false);
            try { acquired = lockChannel.tryLock(); }
            catch (OverlappingFileLockException exception) { throw new IOException("Account store is already in use", exception); }
            if (acquired == null) throw new IOException("Account store is already in use; stop the other server first");
            load();
        } catch (IOException | RuntimeException exception) {
            if (acquired != null) acquired.release();
            lockChannel.close();
            throw exception;
        }
        lock = acquired;
    }

    public synchronized Account find(String username) {
        ensureOpen();
        return accounts.get(username);
    }

    public synchronized int count() { ensureOpen(); return accounts.size(); }

    /** Returns false for a duplicate; memory changes only after the disk commit succeeds. */
    public synchronized boolean create(Account account) throws IOException {
        ensureOpen();
        String username = account.profile().username();
        if (!username.matches("[a-z][a-z0-9_]{2,31}")) throw new IllegalArgumentException("Invalid stored username");
        if (accounts.containsKey(username)) return false;
        if (accounts.size() == MAX_ACCOUNTS) throw new IOException("Account capacity reached");
        Map<String, Account> updated = new LinkedHashMap<>(accounts);
        updated.put(username, account);
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray users = new JsonArray();
        for (Account entry : updated.values()) {
            JsonObject user = new JsonObject();
            user.addProperty("username", entry.profile().username());
            user.addProperty("role", entry.profile().role().name());
            user.addProperty("salt", entry.passwordHash().salt());
            user.addProperty("hash", entry.passwordHash().value());
            user.addProperty("iterations", entry.passwordHash().iterations());
            users.add(user);
        }
        root.add("users", users);
        Path temporary = Files.createTempFile(directory, "users-", ".tmp");
        try {
            restrict(temporary, false);
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(GSON.toJson(root));
                while (bytes.hasRemaining()) output.write(bytes);
                output.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            accounts = updated;
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }

    private void load() throws IOException {
        if (!Files.exists(file)) return;
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Account store exceeds its size limit");
        restrict(file, false);
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !"1".equals(root.get("version").toString())) throw new IllegalArgumentException();
            JsonArray users = root.getAsJsonArray("users");
            if (users.size() > MAX_ACCOUNTS) throw new IllegalArgumentException();
            for (var element : users) {
                JsonObject user = element.getAsJsonObject();
                String username = user.get("username").getAsString();
                if (!username.matches("[a-z][a-z0-9_]{2,31}")) throw new IllegalArgumentException();
                UserProfile profile = new UserProfile(username, Role.valueOf(user.get("role").getAsString()));
                var hash = new PasswordHasher.Hash(user.get("salt").getAsString(), user.get("hash").getAsString(),
                        user.get("iterations").getAsInt());
                if (accounts.putIfAbsent(username, new Account(profile, hash)) != null) throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid account store; restore a valid backup before starting", exception);
        }
    }

    private static void restrict(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Account store is closed"); }

    @Override public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            try { lock.release(); } finally { lockChannel.close(); }
        }
    }
}
