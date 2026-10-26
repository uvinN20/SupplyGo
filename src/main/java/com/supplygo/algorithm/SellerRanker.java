package com.supplygo.algorithm;

import java.util.*;
import com.supplygo.model.BusinessProfile;
import com.supplygo.model.OrderStatus;
import com.supplygo.model.Role;
import com.supplygo.model.SupplyOrder;

/** Higher fulfilled volume, then more fulfilled orders, then username. */
public final class SellerRanker {
    public static final Comparator<SellerPerformance> BEST_FIRST =
            Comparator.comparingLong(SellerPerformance::fulfilledUnits).reversed()
                    .thenComparing(Comparator.comparingInt(SellerPerformance::fulfilledOrders).reversed())
                    .thenComparing(SellerPerformance::username);
    private SellerRanker() { }
    public static List<SellerPerformance> rank(Collection<SellerPerformance> candidates, int limit) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > 10000) throw new IllegalArgumentException("At most 10000 candidates");
        Set<String> users = new HashSet<>();
        for (SellerPerformance candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (!users.add(candidate.username())) throw new IllegalArgumentException("Duplicate seller username");
        }
        return TopK.select(candidates, limit, BEST_FIRST);
    }
    /** Rank saved seller profiles using supplied server snapshots. Input collections are never modified. */
    public static List<SellerPerformance> fromOrders(Collection<BusinessProfile> profiles,
            Collection<SupplyOrder> orders, int limit) {
        Objects.requireNonNull(profiles, "profiles"); Objects.requireNonNull(orders, "orders");
        if (profiles.size() > 10000 || orders.size() > 10000) throw new IllegalArgumentException("Snapshot exceeds 10000 records");
        Map<String, SellerPerformance> totals = new HashMap<>();
        Set<String> users = new HashSet<>();
        for (BusinessProfile profile : profiles) {
            Objects.requireNonNull(profile, "profile");
            if (!users.add(profile.username())) throw new IllegalArgumentException("Duplicate profile username");
            if (profile.role() == Role.SELLER) totals.put(profile.username(), new SellerPerformance(profile.username(), 0, 0));
        }
        Set<Long> orderIds = new HashSet<>();
        for (SupplyOrder order : orders) {
            Objects.requireNonNull(order, "order");
            if (!orderIds.add(order.id())) throw new IllegalArgumentException("Duplicate order ID");
            SellerPerformance current = totals.get(order.sellerUsername());
            if (current != null && order.status() == OrderStatus.FULFILLED)
                totals.put(current.username(), new SellerPerformance(current.username(),
                        Math.addExact(current.fulfilledUnits(), order.quantity()), Math.incrementExact(current.fulfilledOrders())));
        }
        return rank(totals.values(), limit);
    }
}
