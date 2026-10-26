# PriorityQueue ranking foundation

This increment adds pure Java DSA algorithms and a console demonstration. It does not expose a network endpoint or add a Swing ranking screen.

## Supplier ranking

Pass one SupplierCandidate per supplier for the same product, currency and unit basis. Rank in this exact priority order:

1. More remaining freshness days.
2. Lower unit price in integer minor currency units (for example, LKR cents).
3. Shorter distance in integer metres.
4. Username ascending for deterministic ties.

These are lexicographic priorities, not a weighted score: any freshness advantage wins over price or distance. They are an explicit proposed classroom policy, not an objectively optimal procurement rule. Do not compare offers for different products or mix currencies/units. A freshness value of zero is allowed and ranks below positive freshness; this module does not decide food-safety eligibility.

The current SupplyGo business profiles do not contain freshness, price or distance. The console demo uses illustrative values. Production supplier ranking requires a later validated server-side offer/metric store; do not invent metrics from profile location strings or present the demo as live supplier performance.

## Seller ranking

Rank by fulfilled whole fruit units descending, then fulfilled order count descending, then username ascending. These values are not revenue, money or profit. Summing units across fruit products gives volume in the project's existing whole-unit model, not a business-value comparison.

SellerRanker.fromOrders joins saved SELLER BusinessProfile records with supplied SupplyOrder snapshots. It counts FULFILLED orders only, includes saved sellers with no fulfilled orders as zero, excludes supplier profiles, and ignores orders whose seller has no saved business profile. The input list's time range defines the reporting period; no date filter is applied automatically. Duplicate profile usernames and duplicate order IDs are rejected to prevent ambiguous joins and double-counting.

If a future server service exposes rankings, obtain the complete repository snapshots on the server, apply permissions there, and pass immutable snapshots to these algorithms. The frontend must not open server JSON files. The algorithms do not perform authentication themselves.

## Heap behavior and complexity

Both rankers return top K, where K is 1–100 and each input snapshot contains at most 10000 records. TopK uses Java PriorityQueue with the best-first comparator reversed so the worst retained candidate is at the heap head. After each offer, poll if the heap grows past K. At the end, poll the retained candidates and reverse the list into best-first order. Iterating a PriorityQueue directly does not produce sorted output.

For N candidates, selection costs O(N log K), with O(K) heap space; producing ordered results costs O(K log K). Username validation uses O(N) extra space for duplicate detection. Seller aggregation costs O(P + O) expected time and O(P + O) auxiliary space for profiles, totals and duplicate-order checking, before top-K selection; P is profile count and O is order count. Calls use local state and do not mutate input collections; callers should supply stable snapshots rather than concurrently modifying their collections.

## Run

With Java 25 and the existing Maven project:

```text
mvn clean verify
java -cp target/classes com.supplygo.algorithm.RankingDemo
```

Expected supplier order: farm_c, farm_b, farm_a.
Expected seller order: shop_c, shop_b, shop_a.

Tests cover priority direction, tie-breaking, random top-K results versus sorting, invalid inputs, duplicates, fulfilled-only aggregation, zero-history sellers, totals above Integer.MAX_VALUE, immutable output and concurrent independent calls.
