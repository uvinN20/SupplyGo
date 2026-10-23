package com.supplygo.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.*;
import com.supplygo.model.*;
import com.supplygo.storage.InventoryRepository;

@Timeout(15)
class OrderServiceTest {
    @TempDir Path data;
    final UserProfile admin = new UserProfile("admin", Role.ADMIN);
    final UserProfile seller = new UserProfile("seller", Role.SELLER);
    final UserProfile other = new UserProfile("other", Role.SELLER);
    String token() { return UUID.randomUUID().toString(); }
    InventoryService seed(InventoryRepository repo) throws Exception {
        var stock = new InventoryService(repo); stock.createProduct(admin, "APPLE", "Apple");
        stock.receive(admin, "APPLE", 5); stock.receive(admin, "APPLE", 10); return stock;
    }
    void quantities(InventoryService stock, long onHand, long reserved, long available) throws Exception {
        var row = stock.list(admin, false, 0).getAsJsonArray("products").get(0).getAsJsonObject();
        assertEquals(onHand, row.get("onHand").getAsLong()); assertEquals(reserved, row.get("reserved").getAsLong());
        assertEquals(available, row.get("quantity").getAsLong());
    }
    void code(String expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(AuthException.class, action).code());
    }
    @Test void reservationApprovalAndFifoFulfilmentSurviveRestart() throws Exception {
        long id;
        try (var repo = new InventoryRepository(data)) {
            var stock = seed(repo); var orders = new OrderService(repo);
            var order = orders.create(seller, token(), "APPLE", 8); id = order.id();
            quantities(stock, 15, 8, 7);
            code("INSUFFICIENT_STOCK", () -> stock.withdraw(admin, "APPLE", 8));
            code("INVALID_ORDER_STATE", () -> orders.transition(admin, id, OrderStatus.FULFILLED));
            orders.transition(admin, id, OrderStatus.APPROVED); quantities(stock, 15, 8, 7);
        }
        try (var repo = new InventoryRepository(data)) {
            var stock = new InventoryService(repo); var orders = new OrderService(repo);
            assertEquals(OrderStatus.APPROVED, orders.get(seller, id).status()); quantities(stock, 15, 8, 7);
            var fulfilled = orders.transition(admin, id, OrderStatus.FULFILLED);
            assertEquals("admin", fulfilled.updatedBy()); quantities(stock, 7, 0, 7);
            assertEquals(2, repo.snapshot().batches().getFirst().id()); assertEquals(7, repo.snapshot().batches().getFirst().quantity());
            assertEquals(fulfilled, orders.transition(admin, id, OrderStatus.FULFILLED)); quantities(stock, 7, 0, 7);
            code("INVALID_ORDER_STATE", () -> orders.transition(admin, id, OrderStatus.CANCELLED));
        }
        try (var repo = new InventoryRepository(data)) {
            assertEquals(OrderStatus.FULFILLED, new OrderService(repo).get(seller, id).status());
            quantities(new InventoryService(repo), 7, 0, 7);
        }
    }
    @Test void cancellationReleasesReservationWithoutRecreatingBatches() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            var stock = seed(repo); var orders = new OrderService(repo); var before = repo.snapshot().batches();
            var first = orders.create(seller, token(), "APPLE", 8);
            orders.transition(seller, first.id(), OrderStatus.CANCELLED); quantities(stock, 15, 0, 15);
            assertEquals(before, repo.snapshot().batches());
            orders.transition(seller, first.id(), OrderStatus.CANCELLED); quantities(stock, 15, 0, 15);
            var second = orders.create(seller, token(), "APPLE", 15); orders.transition(admin, second.id(), OrderStatus.APPROVED);
            code("FORBIDDEN", () -> orders.transition(seller, second.id(), OrderStatus.CANCELLED));
            orders.transition(admin, second.id(), OrderStatus.CANCELLED); quantities(stock, 15, 0, 15);
        }
    }
    @Test void identitiesRolesAndValidationAreEnforced() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            seed(repo); var orders = new OrderService(repo); var order = orders.create(seller, token(), "APPLE", 1);
            assertTrue(orders.list(other, 0).orders().isEmpty());
            code("ORDER_NOT_FOUND", () -> orders.get(other, order.id()));
            code("ORDER_NOT_FOUND", () -> orders.transition(other, order.id(), OrderStatus.CANCELLED));
            code("FORBIDDEN", () -> orders.transition(seller, order.id(), OrderStatus.APPROVED));
            for (Role role : Role.values()) {
                var actor = new UserProfile("account", role);
                if (OrderService.isManager(role)) {
                    assertEquals(1, orders.list(actor, 0).orders().size());
                    assertEquals(OrderStatus.APPROVED, orders.transition(actor, order.id(), OrderStatus.APPROVED).status());
                } else if (role != Role.SELLER) {
                    code("FORBIDDEN", () -> orders.list(actor, 0)); code("FORBIDDEN", () -> orders.get(actor, order.id()));
                }
                if (role != Role.SELLER) code("FORBIDDEN", () -> orders.create(actor, token(), "APPLE", 1));
            }
            code("AUTH_REQUIRED", () -> orders.list(null, 0));
            code("INVALID_ORDER", () -> orders.create(seller, "bad", "APPLE", 1));
            code("INVALID_ORDER", () -> orders.create(seller, token(), "APPLE", 0));
            code("PRODUCT_NOT_FOUND", () -> orders.create(seller, token(), "MISSING", 1));
            code("INVALID_ORDER", () -> orders.list(seller, -1));
        }
    }
    @Test void submissionRetriesAreScopedToSellerAndPersistAcrossRestart() throws Exception {
        String key = token(); long id;
        try (var repo = new InventoryRepository(data)) {
            var stock = seed(repo); var orders = new OrderService(repo);
            var first = orders.create(seller, key, "APPLE", 5); id = first.id();
            assertEquals(first, orders.create(seller, key, "APPLE", 5)); quantities(stock, 15, 5, 10);
            code("SUBMISSION_CONFLICT", () -> orders.create(seller, key, "APPLE", 6));
            assertNotEquals(id, orders.create(other, key, "APPLE", 5).id()); quantities(stock, 15, 10, 5);
        }
        try (var repo = new InventoryRepository(data)) {
            var orders = new OrderService(repo); assertEquals(id, orders.create(seller, key, "APPLE", 5).id());
            orders.transition(seller, id, OrderStatus.CANCELLED);
            assertEquals(OrderStatus.CANCELLED, orders.create(seller, key, "APPLE", 5).status());
            assertEquals(2, repo.snapshot().orders().size()); quantities(new InventoryService(repo), 15, 5, 10);
        }
    }
    @Test void competingOrdersAndManualWithdrawalsShareOneLock() throws Exception {
        try (var repo = new InventoryRepository(data); var pool = Executors.newFixedThreadPool(12)) {
            var stock = new InventoryService(repo); stock.createProduct(admin, "APPLE", "Apple"); stock.receive(admin, "APPLE", 100);
            var orders = new OrderService(repo); CountDownLatch start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 20; i++) {
                boolean order = i % 2 == 0;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        if (order) orders.create(seller, token(), "APPLE", 10);
                        else new InventoryService(repo).withdraw(admin, "APPLE", 10);
                        return true;
                    } catch (AuthException e) { assertEquals("INSUFFICIENT_STOCK", e.code()); return false; }
                }));
            }
            start.countDown(); int succeeded = 0;
            for (var future : futures) if (future.get(5, TimeUnit.SECONDS)) succeeded++;
            assertEquals(10, succeeded);
            long reserved = repo.snapshot().orders().size() * 10L; quantities(stock, reserved, reserved, 0);
        }
    }
    @Test void concurrentRetriesCreateExactlyOneOrder() throws Exception {
        try (var repo = new InventoryRepository(data); var pool = Executors.newFixedThreadPool(8)) {
            var stock = seed(repo); String key = token(); CountDownLatch start = new CountDownLatch(1);
            var futures = new ArrayList<Future<SupplyOrder>>();
            for (int i = 0; i < 16; i++) futures.add(pool.submit(() -> { start.await(); return new OrderService(repo).create(seller, key, "APPLE", 5); }));
            start.countDown(); Set<Long> ids = new HashSet<>();
            for (var future : futures) ids.add(future.get(5, TimeUnit.SECONDS).id());
            assertEquals(Set.of(1L), ids); assertEquals(1, repo.snapshot().orders().size()); quantities(stock, 15, 5, 10);
        }
    }
    @Test void racingCancellationAndFulfilmentCannotBothSucceed() throws Exception {
        try (var repo = new InventoryRepository(data); var pool = Executors.newFixedThreadPool(2)) {
            var stock = seed(repo); var orders = new OrderService(repo);
            var order = orders.create(seller, token(), "APPLE", 8); orders.transition(admin, order.id(), OrderStatus.APPROVED);
            CountDownLatch start = new CountDownLatch(1); var futures = new ArrayList<Future<OrderStatus>>();
            for (var target : List.of(OrderStatus.FULFILLED, OrderStatus.CANCELLED)) futures.add(pool.submit(() -> {
                start.await();
                try { return orders.transition(admin, order.id(), target).status(); }
                catch (AuthException e) { assertEquals("INVALID_ORDER_STATE", e.code()); return null; }
            }));
            start.countDown(); int successes = 0;
            for (var future : futures) if (future.get(5, TimeUnit.SECONDS) != null) successes++;
            assertEquals(1, successes); var finalStatus = orders.get(seller, order.id()).status();
            long onHand = finalStatus == OrderStatus.FULFILLED ? 7 : 15; quantities(stock, onHand, 0, onHand);
        }
    }
    @Test void failedCommitDoesNotPublishOrderOrConsumeStock() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            seed(repo); var orders = new OrderService(repo); var order = orders.create(seller, token(), "APPLE", 8);
            orders.transition(admin, order.id(), OrderStatus.APPROVED); var before = repo.snapshot();
            Files.delete(data.resolve("inventory.json")); Files.createDirectory(data.resolve("inventory.json"));
            Files.writeString(data.resolve("inventory.json/block"), "block");
            code("STORAGE_ERROR", () -> orders.transition(admin, order.id(), OrderStatus.FULFILLED));
            assertEquals(before, repo.snapshot());
            code("STORAGE_ERROR", () -> orders.create(other, token(), "APPLE", 1)); assertEquals(before, repo.snapshot());
            code("STORAGE_ERROR", () -> orders.transition(admin, order.id(), OrderStatus.CANCELLED)); assertEquals(before, repo.snapshot());
            try (var files = Files.list(data)) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".tmp"))); }
        }
    }
    @Test void legacyInventoryIsPreservedAndUpgradedOnFirstMutation() throws Exception {
        String old = """
                {"version":1,"nextBatchId":7,"products":[{"id":"APPLE","name":"Apple"}],
                "batches":[{"id":6,"productId":"APPLE","quantity":20,"receivedAt":"2026-10-01T00:00:00Z","receivedBy":"admin"}]}
                """;
        Files.writeString(data.resolve("inventory.json"), old);
        try (var repo = new InventoryRepository(data)) {
            assertEquals(old, Files.readString(data.resolve("inventory.json"))); assertEquals(2, repo.snapshot().version());
            var orders = new OrderService(repo); var order = orders.create(seller, token(), "APPLE", 5);
            assertEquals(1, order.id()); assertEquals(6, repo.snapshot().batches().getFirst().id());
            new InventoryService(repo).receive(admin, "APPLE", 1); assertEquals(7, repo.snapshot().batches().getLast().id());
        }
        try (var repo = new InventoryRepository(data)) {
            assertEquals(1, repo.snapshot().orders().size()); quantities(new InventoryService(repo), 21, 5, 16);
        }
    }
    @Test void corruptedReservationsOrMissingOrdersFailClosed() throws Exception {
        try (var repo = new InventoryRepository(data)) { seed(repo); new OrderService(repo).create(seller, token(), "APPLE", 5); }
        String source = Files.readString(data.resolve("inventory.json"));
        var json = JsonParser.parseString(source).getAsJsonObject();
        json.getAsJsonArray("orders").get(0).getAsJsonObject().addProperty("quantity", 100);
        Files.writeString(data.resolve("inventory.json"), json.toString());
        assertThrows(IOException.class, () -> new InventoryRepository(data));
        json = JsonParser.parseString(source).getAsJsonObject(); json.remove("orders");
        Files.writeString(data.resolve("inventory.json"), json.toString()); assertThrows(IOException.class, () -> new InventoryRepository(data));
    }
    @Test void pagingFiltersBeforeApplyingOffsets() throws Exception {
        try (var repo = new InventoryRepository(data)) {
            seed(repo); var orders = new OrderService(repo);
            orders.create(other, token(), "APPLE", 1);
            for (int i = 0; i < 11; i++) orders.create(seller, token(), "APPLE", 1);
            assertEquals(10, orders.list(seller, 0).nextOffset()); assertEquals(2, orders.list(seller, 0).orders().getFirst().id());
            assertEquals(1, orders.list(seller, 10).orders().size()); assertEquals(-1, orders.list(seller, 10).nextOffset());
            assertEquals(1, orders.list(other, 0).orders().size()); assertEquals(2, orders.list(admin, 10).orders().size());
        }
    }
}
