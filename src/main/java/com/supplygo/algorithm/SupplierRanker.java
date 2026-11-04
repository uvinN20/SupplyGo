package com.supplygo.algorithm;

import java.util.*;

/** Freshness first, then lower unit price, shorter distance, and username. */
public final class SupplierRanker {
    public static final Comparator<SupplierCandidate> BEST_FIRST =
            Comparator.comparingInt(SupplierCandidate::freshnessDaysRemaining).reversed()
                    .thenComparingLong(SupplierCandidate::unitPriceMinor)
                    .thenComparingInt(SupplierCandidate::distanceMeters)
                    .thenComparing(SupplierCandidate::username);
    private SupplierRanker() { }
    public static List<SupplierCandidate> rank(Collection<SupplierCandidate> candidates, int limit) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > 10000) throw new IllegalArgumentException("At most 10000 candidates");
        Set<String> users = new HashSet<>();
        for (SupplierCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (!users.add(candidate.username())) throw new IllegalArgumentException("Duplicate supplier username");
        }
        return TopK.select(candidates, limit, BEST_FIRST);
    }
}
