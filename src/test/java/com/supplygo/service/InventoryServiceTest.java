package com.supplygo.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.supplygo.model.*;
import com.supplygo.storage.InventoryRepository;
import com.supplygo.protocol.*;

class InventoryServiceTest {
    @TempDir Path data;
    private final UserProfile admin = new UserProfile("admin", Role.ADMIN);

    @Test void fifoAcrossBatchesAndRestartPreservesPartialBatchAndSequence() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            var service = new InventoryService(repo);
            service.createProduct(admin, "APPLE", "Apple"); service.createProduct(admin, "MANGO", "Mango");
            service.receive(admin, "APPLE", 5); service.receive(admin, "MANGO", 7); service.receive(admin, "APPLE", 10);
            service.withdraw(admin, "APPLE", 8);
            assertEquals(List.of(2L, 3L), repo.snapshot().batches().stream().map(FruitBatch::id).toList());
            assertEquals(List.of(7, 7), repo.snapshot().batches().stream().map(FruitBatch::quantity).toList());
        }
        try (var repo = new InventoryRepository(data)) {
            var service = new InventoryService(repo); service.receive(admin, "APPLE", 1);
            assertEquals(4, repo.snapshot().batches().getLast().id());
            assertEquals(8, service.list(admin, false, 0).getAsJsonArray("products").get(0).getAsJsonObject().get("quantity").getAsInt());
        }
    }
    @Test void concurrentWithdrawalsNeverOversell() throws Exception {
        try (var repo = new InventoryRepository(data); var pool = Executors.newFixedThreadPool(12)) {
            var service = new InventoryService(repo); service.createProduct(admin, "APPLE", "Apple"); service.receive(admin, "APPLE", 100);
            CountDownLatch start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 20; i++) futures.add(pool.submit(() -> {
                start.await();
                try { service.withdraw(admin, "APPLE", 10); return true; }
                catch (AuthException e) { assertEquals("INSUFFICIENT_STOCK", e.code()); return false; }
            }));
            start.countDown(); int success = 0;
            for (var future : futures) if (future.get(5, TimeUnit.SECONDS)) success++;
            assertEquals(10, success); assertTrue(repo.snapshot().batches().isEmpty());
        }
    }
    @Test void permissionsValidationAndInsufficientStockDoNotMutate() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            var service = new InventoryService(repo); service.createProduct(admin, "APPLE", "Apple"); service.receive(admin, "APPLE", 5);
            var before = repo.snapshot();
            for (Role role : Role.values()) {
                var actor = new UserProfile("account", role);
                assertNotNull(service.list(actor, false, 0));
                if (role != Role.ADMIN && role != Role.PRODUCTION_MANAGER) {
                    assertEquals("FORBIDDEN", assertThrows(AuthException.class, () -> service.receive(actor, "APPLE", 1)).code());
                    assertEquals("FORBIDDEN", assertThrows(AuthException.class, () -> service.withdraw(actor, "APPLE", 1)).code());
                    assertEquals("FORBIDDEN", assertThrows(AuthException.class, () -> service.createProduct(actor, "PEAR", "Pear")).code());
                }
            }
            assertEquals("AUTH_REQUIRED", assertThrows(AuthException.class, () -> service.list(null, false, 0)).code());
            assertEquals("PRODUCT_EXISTS", assertThrows(AuthException.class, () -> service.createProduct(admin, "APPLE", "Again")).code());
            assertEquals("INVALID_INVENTORY", assertThrows(AuthException.class, () -> service.createProduct(admin, "bad", "Name")).code());
            assertEquals("INVALID_INVENTORY", assertThrows(AuthException.class, () -> service.receive(admin, "APPLE", 0)).code());
            assertEquals("PRODUCT_NOT_FOUND", assertThrows(AuthException.class, () -> service.receive(admin, "MISSING", 1)).code());
            assertEquals("INSUFFICIENT_STOCK", assertThrows(AuthException.class, () -> service.withdraw(admin, "APPLE", 6)).code());
            assertEquals(before, repo.snapshot());
        }
    }
    @Test void failedAtomicReplaceDoesNotPublishChanges() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            var service = new InventoryService(repo); service.createProduct(admin, "APPLE", "Apple");
            var before = repo.snapshot(); Files.delete(data.resolve("inventory.json")); Files.createDirectory(data.resolve("inventory.json"));
            Files.writeString(data.resolve("inventory.json/block"), "block");
            assertEquals("STORAGE_ERROR", assertThrows(AuthException.class, () -> service.receive(admin, "APPLE", 10)).code());
            assertEquals(before, repo.snapshot());
            try (var files = Files.list(data)) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".tmp"))); }
        }
    }
    @Test void processLockAndCorruptDataFailClosed() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            assertThrows(java.io.IOException.class, () -> new InventoryRepository(data));
        }
        Files.writeString(data.resolve("inventory.json"), "{\"version\":1,\"nextBatchId\":1,\"products\":[],\"batches\":[{\"id\":1,\"productId\":\"MISSING\",\"quantity\":2}]}");
        assertThrows(java.io.IOException.class, () -> new InventoryRepository(data));
        assertTrue(Files.readString(data.resolve("inventory.json")).contains("MISSING"));
    }
    @Test void pagesStayInsideFrameLimitAndRoundTripStrictCodec() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            var service = new InventoryService(repo);
            for (int i = 0; i < 11; i++) { service.createProduct(admin, "P" + i, "果".repeat(60)); service.receive(admin, "P" + i, 10); }
            for (boolean batches : List.of(false, true)) {
                var first = service.list(admin, batches, 0); assertEquals(10, first.get("nextOffset").getAsInt());
                var frame = ProtocolCodec.encode(Response.ok("id", batches ? "BATCHES" : "PRODUCTS", first));
                var out = new java.io.ByteArrayOutputStream(); FrameIO.write(out, frame);
                assertEquals(first, ProtocolCodec.decodeResponse(frame).attributes());
                assertEquals(-1, service.list(admin, batches, 10).get("nextOffset").getAsInt());
            }
        }
    }
}
