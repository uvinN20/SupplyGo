package com.supplygo.algorithm;

import java.util.List;

/** Illustrative input only. This demo never opens or changes server files. */
public final class RankingDemo {
    private RankingDemo() { }
    public static void main(String[] args) {
        System.out.println("Illustrative supplier candidates: same product and currency");
        SupplierRanker.rank(List.of(
                new SupplierCandidate("farm_a", 4, 18000, 15000),
                new SupplierCandidate("farm_b", 6, 20000, 25000),
                new SupplierCandidate("farm_c", 6, 19000, 20000)), 3)
                .forEach(System.out::println);
        System.out.println("Illustrative seller performance: whole fulfilled units, not revenue");
        SellerRanker.rank(List.of(
                new SellerPerformance("shop_a", 300, 5),
                new SellerPerformance("shop_b", 500, 8),
                new SellerPerformance("shop_c", 500, 10)), 3)
                .forEach(System.out::println);
    }
}
