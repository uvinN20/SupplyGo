package com.supplygo.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.supplygo.model.*;

class SellerRankerTest {
    BusinessProfile profile(String user, Role role) { return new BusinessProfile(user, role, "Person", "Business", "0771234567", "Galle", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"); }
    SupplyOrder order(long id, String user, int units, OrderStatus status) {
        return new SupplyOrder(id, UUID.randomUUID().toString(), user, "APPLE", units, status,
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", "admin");
    }
    @Test void volumeThenOrderCountThenUsernameBreakTies() {
        var data = List.of(new SellerPerformance("zulu", 100, 2), new SellerPerformance("alpha", 100, 2),
                new SellerPerformance("more_orders", 100, 3), new SellerPerformance("lower", 90, 4));
        assertEquals(List.of("more_orders", "alpha", "zulu", "lower"), SellerRanker.rank(data, 10).stream().map(SellerPerformance::username).toList());
    }
    @Test void aggregationCountsOnlyFulfilledOrdersAndSavedSellerProfiles() {
        var profiles = List.of(profile("seller_a", Role.SELLER), profile("seller_b", Role.SELLER), profile("supplier", Role.SUPPLIER));
        var orders = List.of(order(1, "seller_a", 10, OrderStatus.FULFILLED), order(2, "seller_a", 20, OrderStatus.FULFILLED),
                order(3, "seller_b", 999, OrderStatus.PENDING), order(4, "seller_b", 999, OrderStatus.APPROVED),
                order(5, "seller_b", 999, OrderStatus.CANCELLED), order(6, "no_profile", 999, OrderStatus.FULFILLED));
        assertEquals(List.of(new SellerPerformance("seller_a", 30, 2), new SellerPerformance("seller_b", 0, 0)), SellerRanker.fromOrders(profiles, orders, 10));
        assertEquals(6, orders.size()); assertEquals(3, profiles.size());
    }
    @Test void duplicateSnapshotsAndInvalidPerformanceAreRejected() {
        var seller = profile("seller", Role.SELLER); var fulfilled = order(1, "seller", 10, OrderStatus.FULFILLED);
        assertThrows(IllegalArgumentException.class, () -> SellerRanker.fromOrders(List.of(seller, seller), List.of(), 10));
        assertThrows(IllegalArgumentException.class, () -> SellerRanker.fromOrders(List.of(seller), List.of(fulfilled, fulfilled), 10));
        assertThrows(IllegalArgumentException.class, () -> new SellerPerformance("seller", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SellerPerformance("seller", 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new SellerPerformance("seller", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SellerPerformance("seller", 1, 2));
        assertThrows(IllegalArgumentException.class, () -> SellerRanker.rank(List.of(new SellerPerformance("seller", 1, 1), new SellerPerformance("seller", 2, 1)), 10));
    }
    @Test void moreThanIntMaximumUnitsDoNotOverflow() {
        List<SupplyOrder> orders = new ArrayList<>();
        for (int i = 1; i <= 3000; i++) orders.add(order(i, "seller", 1_000_000, OrderStatus.FULFILLED));
        assertEquals(3_000_000_000L, SellerRanker.fromOrders(List.of(profile("seller", Role.SELLER)), orders, 1).getFirst().fulfilledUnits());
    }
    @Test void emptyListsNullsAndLimitsAreHandled() {
        assertTrue(SellerRanker.fromOrders(List.of(), List.of(), 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SellerRanker.rank(List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> SellerRanker.rank(List.of(), 101));
        assertThrows(NullPointerException.class, () -> SellerRanker.fromOrders(null, List.of(), 1));
        assertThrows(NullPointerException.class, () -> SellerRanker.fromOrders(List.of(), null, 1));
        assertThrows(NullPointerException.class, () -> SellerRanker.rank(Arrays.asList((SellerPerformance)null), 1));
    }
    @Test void boundedResultsMatchIndependentSortAcrossConcurrentCalls() throws Exception {
        Random random = new Random(42); List<SellerPerformance> data = new ArrayList<>();
        for (int i = 0; i < 500; i++) data.add(new SellerPerformance("seller" + i, random.nextLong(500, 100000), random.nextInt(1, 500)));
        List<SellerPerformance> expected = data.stream().sorted(Comparator.comparingLong(SellerPerformance::fulfilledUnits).reversed()
                .thenComparing(Comparator.comparingInt(SellerPerformance::fulfilledOrders).reversed()).thenComparing(SellerPerformance::username)).limit(10).toList();
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<List<SellerPerformance>>> work = new ArrayList<>();
            for (int i = 0; i < 30; i++) work.add(() -> SellerRanker.rank(data, 10));
            for (var result : pool.invokeAll(work)) assertEquals(expected, result.get(5, TimeUnit.SECONDS));
        }
        assertThrows(UnsupportedOperationException.class, () -> SellerRanker.rank(data, 10).clear());
    }
}
