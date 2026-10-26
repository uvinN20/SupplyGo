package com.supplygo.algorithm;

import java.util.*;

/** Bounded heap: worst retained entry is at the head. No shared mutable state. */
final class TopK {
    private TopK() { }
    static <T> List<T> select(Collection<T> input, int limit, Comparator<T> bestFirst) {
        Objects.requireNonNull(input, "input");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Limit must be 1–100");
        if (input.size() > 10000) throw new IllegalArgumentException("At most 10000 candidates");
        PriorityQueue<T> heap = new PriorityQueue<>(bestFirst.reversed());
        for (T candidate : input) {
            heap.add(Objects.requireNonNull(candidate, "candidate"));
            if (heap.size() > limit) heap.poll();
        }
        List<T> result = new ArrayList<>(heap.size());
        while (!heap.isEmpty()) result.add(heap.poll());
        Collections.reverse(result);
        return List.copyOf(result);
    }
}
