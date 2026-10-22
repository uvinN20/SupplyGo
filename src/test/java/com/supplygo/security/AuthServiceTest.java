package com.supplygo.security;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.supplygo.model.*;
import com.supplygo.service.*;
import com.supplygo.storage.AccountRepository;

class AuthServiceTest {
    @TempDir Path directory;
    private static final String PASSWORD = "a strong test password";
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    @Test void hashesHaveUniqueSaltsAndVerifyWithoutPlaintext() {
        var hasher = new PasswordHasher();
        var first = hasher.hash(PASSWORD.toCharArray());
        var second = hasher.hash(PASSWORD.toCharArray());
        assertNotEquals(first.salt(), second.salt());
        assertNotEquals(first.value(), second.value());
        assertTrue(hasher.verify(PASSWORD.toCharArray(), first));
        assertFalse(hasher.verify("wrong password".toCharArray(), first));
        assertFalse(first.toString().contains(first.value()));
    }

    @Test void savesOnlyHashesAndLogsInAfterReopeningStore() throws Exception {
        try (var repository = new AccountRepository(directory)) {
            var service = new AuthService(repository);
            assertEquals(new UserProfile("supplier_one", Role.SUPPLIER), service.register(" Supplier_One ", PASSWORD.toCharArray(), Role.SUPPLIER));
            assertEquals("USERNAME_TAKEN", assertThrows(AuthException.class,
                    () -> service.register("SUPPLIER_ONE", PASSWORD.toCharArray(), Role.SELLER)).code());
            assertEquals(1, repository.count());
        }
        String persisted = Files.readString(directory.resolve("users.json"));
        assertFalse(persisted.contains(PASSWORD));
        assertTrue(persisted.contains("600000"));
        try (var reopened = new AccountRepository(directory)) {
            assertEquals(Role.SUPPLIER, new AuthService(reopened).login("SUPPLIER_ONE", PASSWORD.toCharArray()).role());
        }
    }

    @Test void rejectsPrivilegeEscalationWeakPasswordsAndInvalidNames() throws Exception {
        try (var repository = new AccountRepository(directory)) {
            var service = new AuthService(repository);
            assertEquals("FORBIDDEN", assertThrows(AuthException.class,
                    () -> service.register("someone", PASSWORD.toCharArray(), Role.ADMIN)).code());
            for (String name : new String[]{"a", "12name", "../escape", "name with space"}) {
                assertThrows(AuthException.class, () -> service.register(name, PASSWORD.toCharArray(), Role.SELLER));
            }
            assertThrows(AuthException.class, () -> service.register("valid", "short".toCharArray(), Role.SELLER));
            assertThrows(AuthException.class, () -> service.register("valid", new char[129], Role.SELLER));
            assertEquals(0, repository.count());
        }
    }

    @Test void failedAttemptsAreThrottledAcrossConnectionsAndRecoverAfterCooldown() throws Exception {
        MutableClock clock = new MutableClock();
        try (var repository = new AccountRepository(directory)) {
            var service = new AuthService(repository, clock);
            service.provision("admin", PASSWORD.toCharArray(), Role.ADMIN);
            for (int i = 0; i < 5; i++) assertEquals("INVALID_CREDENTIALS", assertThrows(AuthException.class,
                    () -> service.login("admin", "incorrect password".toCharArray())).code());
            assertEquals("RATE_LIMITED", assertThrows(AuthException.class,
                    () -> service.login("admin", PASSWORD.toCharArray())).code());
            clock.now = clock.now.plusSeconds(61);
            assertEquals(Role.ADMIN, service.login("admin", PASSWORD.toCharArray()).role());
            assertEquals("INVALID_CREDENTIALS", assertThrows(AuthException.class,
                    () -> service.login("unknown", PASSWORD.toCharArray())).code());
        }
    }

    @Test void concurrentDuplicateRegistrationCreatesExactlyOneAccount() throws Exception {
        try (var repository = new AccountRepository(directory); var pool = Executors.newFixedThreadPool(2)) {
            var service = new AuthService(repository);
            Callable<String> register = () -> {
                try { service.register("same_user", PASSWORD.toCharArray(), Role.SELLER); return "CREATED"; }
                catch (AuthException exception) { return exception.code(); }
            };
            var results = pool.invokeAll(java.util.List.of(register, register));
            var codes = new java.util.HashSet<String>();
            for (var result : results) codes.add(result.get());
            assertEquals(java.util.Set.of("CREATED", "USERNAME_TAKEN"), codes);
            assertEquals(1, repository.count());
        }
    }

    @Test void failedDiskWriteDoesNotPublishAccountOrLeaveTemporaryFile() throws Exception {
        try (var repository = new AccountRepository(directory)) {
            var service = new AuthService(repository);
            Files.createDirectory(directory.resolve("users.json"));
            assertEquals("STORAGE_ERROR", assertThrows(AuthException.class,
                    () -> service.register("new_user", PASSWORD.toCharArray(), Role.SELLER)).code());
            assertEquals(0, repository.count());
            try (var files = Files.list(directory)) { assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp"))); }
        }
    }

    @Test void rejectsCorruptStoresAndConcurrentProcesses() throws Exception {
        try (var repository = new AccountRepository(directory)) {
            assertThrows(IOException.class, () -> new AccountRepository(directory));
        }
        Files.writeString(directory.resolve("users.json"), "corrupt");
        assertThrows(IOException.class, () -> new AccountRepository(directory));
        assertEquals("corrupt", Files.readString(directory.resolve("users.json")));
    }

    @Test void sessionsExpireAndServerChecksTheActualRole() throws Exception {
        MutableClock clock = new MutableClock();
        UserSession session = new UserSession(clock);
        assertEquals("AUTH_REQUIRED", assertThrows(AuthException.class, session::requireUser).code());
        session.login(new UserProfile("supplier", Role.SUPPLIER));
        assertEquals("FORBIDDEN", assertThrows(AuthException.class, () -> session.requireRole(Role.ADMIN)).code());
        clock.now = clock.now.plusSeconds(900);
        assertEquals("SESSION_EXPIRED", assertThrows(AuthException.class, session::requireUser).code());
        assertEquals("AUTH_REQUIRED", assertThrows(AuthException.class, session::requireUser).code());
        session.login(new UserProfile("admin", Role.ADMIN));
        assertEquals(Role.ADMIN, session.requireRole(Role.ADMIN).role());
        session.logout();
        assertThrows(AuthException.class, session::requireUser);
    }
}
