package com.supplygo.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.supplygo.model.*;
import com.supplygo.storage.BusinessProfileRepository;

class BusinessProfileServiceTest {
    @TempDir Path data;
    final UserProfile seller = new UserProfile("seller", Role.SELLER);
    BusinessProfile save(BusinessProfileService service, UserProfile actor) throws Exception {
        return service.update(actor, "Test Person", "Fruit Business", "+94 771234567", "Matara");
    }
    @Test void createUpdateRestartPreservesIdentityAndCreatedTime() throws Exception {
        BusinessProfile saved;
        try (var repo = new BusinessProfileRepository(data)) {
            var service = new BusinessProfileService(repo, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
            assertEquals("PROFILE_NOT_FOUND", assertThrows(AuthException.class, () -> service.get(seller)).code());
            var first = save(service, seller);
            var later = new BusinessProfileService(repo, Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));
            saved = later.update(seller, "Updated Person", "New Business", "0771234567", "Galle");
            assertEquals(first.createdAt(), saved.createdAt()); assertNotEquals(first.updatedAt(), saved.updatedAt());
            assertEquals("seller", saved.username()); assertEquals(Role.SELLER, saved.role());
            var rollback = new BusinessProfileService(repo, Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC));
            saved = save(rollback, seller); assertEquals("2026-01-02T00:00:00Z", saved.updatedAt());
        }
        try (var repo = new BusinessProfileRepository(data)) { assertEquals(saved, new BusinessProfileService(repo).get(seller)); }
    }
    @Test void allRolesHaveOnlyTheirOwnPermissions() throws Exception {
        try (var repo = new BusinessProfileRepository(data)) {
            var service = new BusinessProfileService(repo);
            save(service, seller); save(service, new UserProfile("supplier", Role.SUPPLIER));
            for (Role role : Role.values()) {
                var actor = new UserProfile(role.name().toLowerCase(), role);
                if (role == Role.SUPPLIER || role == Role.SELLER) {
                    assertEquals(actor.username(), save(service, actor).username());
                } else {
                    assertEquals("FORBIDDEN", assertThrows(AuthException.class, () -> save(service, actor)).code());
                    assertEquals("FORBIDDEN", assertThrows(AuthException.class, () -> service.get(actor)).code());
                }
                for (Role directory : List.of(Role.SUPPLIER, Role.SELLER)) {
                    boolean allowed = role == Role.ADMIN || directory == Role.SUPPLIER && role == Role.SUPPLIER_MANAGER
                            || directory == Role.SELLER && role == Role.SELLER_MANAGER;
                    if (allowed) assertTrue(service.list(actor, directory, 0).profiles().stream().allMatch(p -> p.role() == directory));
                    else assertEquals("FORBIDDEN", assertThrows(AuthException.class, () -> service.list(actor, directory, 0)).code());
                }
            }
            assertEquals("AUTH_REQUIRED", assertThrows(AuthException.class, () -> service.get(null)).code());
            assertEquals("AUTH_REQUIRED", assertThrows(AuthException.class, () -> save(service, null)).code());
            assertEquals("AUTH_REQUIRED", assertThrows(AuthException.class, () -> service.list(null, Role.SELLER, 0)).code());
            assertEquals("PROFILE_NOT_FOUND", assertThrows(AuthException.class, () -> service.get(new UserProfile("other", Role.SELLER))).code());
        }
    }
    @Test void invalidDetailsDoNotChangeExistingState() throws Exception {
        try (var repo = new BusinessProfileRepository(data)) {
            var service = new BusinessProfileService(repo); save(service, seller); var before = repo.snapshot();
            for (String value : List.of("", " ", "A".repeat(65), "line\nline", " leading", "trailing ", "\uD800"))
                assertEquals("INVALID_PROFILE", assertThrows(AuthException.class, () -> service.update(seller, value, "Business", "0771234567", "Galle")).code());
            assertThrows(AuthException.class, () -> service.update(seller, "Name", "B".repeat(97), "0771234567", "Galle"));
            assertThrows(AuthException.class, () -> service.update(seller, "Name", "Business", "abc12345", "Galle"));
            assertThrows(AuthException.class, () -> service.update(seller, "Name", "Business", "0771234567", "L".repeat(65)));
            assertThrows(AuthException.class, () -> service.update(seller, "<".repeat(64), "&".repeat(96), "0771234567", "Galle"));
            assertEquals(before, repo.snapshot());
            assertEquals("0771234567", save(service, seller).contactNumber().replace("+94 ", "0"));
        }
    }
    @Test void directoriesFilterBeforePagingAndSortUsernames() throws Exception {
        try (var repo = new BusinessProfileRepository(data)) {
            var service = new BusinessProfileService(repo); var manager = new UserProfile("admin", Role.ADMIN);
            assertTrue(service.list(manager, Role.SELLER, 0).profiles().isEmpty());
            for (int i = 20; i >= 0; i--) {
                save(service, new UserProfile("seller%02d".formatted(i), Role.SELLER));
                save(service, new UserProfile("supplier%02d".formatted(i), Role.SUPPLIER));
            }
            var first = service.list(manager, Role.SELLER, 0); assertEquals(10, first.nextOffset());
            assertEquals("seller00", first.profiles().getFirst().username());
            assertEquals(20, service.list(manager, Role.SELLER, 10).nextOffset());
            assertEquals(1, service.list(manager, Role.SELLER, 20).profiles().size());
            assertEquals(-1, service.list(manager, Role.SELLER, 20).nextOffset());
            assertTrue(service.list(manager, Role.SELLER, 10000).profiles().isEmpty());
            assertThrows(AuthException.class, () -> service.list(manager, Role.SELLER, -1));
            assertThrows(AuthException.class, () -> service.list(manager, Role.SELLER, 10001));
        }
    }
    @Test void concurrentServicesPreserveUnrelatedProfilesAndCreationTimes() throws Exception {
        try (var repo = new BusinessProfileRepository(data); var pool = Executors.newFixedThreadPool(8)) {
            var service = new BusinessProfileService(repo); var other = new BusinessProfileService(repo);
            var first = save(service, seller); List<Callable<Void>> work = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                final int id = i;
                work.add(() -> { save(other, new UserProfile("account" + id, Role.SELLER)); save(service, seller); return null; });
            }
            for (var result : pool.invokeAll(work)) result.get(10, TimeUnit.SECONDS);
            assertEquals(41, repo.snapshot().size()); assertEquals(first.createdAt(), service.get(seller).createdAt());
        }
        try (var repo = new BusinessProfileRepository(data)) { assertEquals(41, repo.snapshot().size()); }
    }
    @Test void failedAtomicReplacementKeepsPriorMemoryAndCleansTemporaryFile() throws Exception {
        try (var repo = new BusinessProfileRepository(data)) {
            var service = new BusinessProfileService(repo); save(service, seller); var before = repo.snapshot();
            Path file = data.resolve("business-profiles.json"); Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("block"), "block");
            assertEquals("STORAGE_ERROR", assertThrows(AuthException.class, () -> service.update(seller, "Changed", "Business", "0771234567", "Galle")).code());
            assertEquals(before, repo.snapshot());
            try (var files = Files.list(data)) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".tmp"))); }
        }
    }
    @Test void processLockCorruptSchemaAndInvalidRowsFailClosed() throws Exception {
        try (var repo = new BusinessProfileRepository(data)) { assertThrows(java.io.IOException.class, () -> new BusinessProfileRepository(data)); }
        for (String invalid : List.of("null", "{}", "{\"version\":2,\"profiles\":[]}", "{\"version\":1,\"profiles\":[{}]}", "{\"version\":1,\"profiles\":[],\"extra\":0}")) {
            Files.writeString(data.resolve("business-profiles.json"), invalid);
            assertThrows(java.io.IOException.class, () -> new BusinessProfileRepository(data));
        }
    }
}
