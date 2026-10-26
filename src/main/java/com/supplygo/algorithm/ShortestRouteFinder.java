package com.supplygo.algorithm;

import java.util.*;
import com.supplygo.model.RouteGraph;

/** Dijkstra with a distance heap, stale-entry rejection and stable ID tie-breaking. */
public final class ShortestRouteFinder {
    public record Result(List<String> path, long meters) {
        public Result { path = List.copyOf(path); if (path.isEmpty() || path.size() > 100 || meters < 0 || meters > 99_000_000_000L) throw new IllegalArgumentException("Invalid route result"); }
    }
    private record Entry(String id, long distance) { }
    private record Edge(String to, long meters) { }
    private ShortestRouteFinder() { }
    public static Optional<Result> find(RouteGraph graph, String from, String to) {
        Map<String, List<Edge>> adjacency = new TreeMap<>(); graph.locations().forEach(l -> adjacency.put(l.id(), new ArrayList<>()));
        if (!adjacency.containsKey(from) || !adjacency.containsKey(to)) throw new IllegalArgumentException("Unknown location ID");
        for (var road : graph.roads()) {
            adjacency.get(road.from()).add(new Edge(road.to(), road.meters())); adjacency.get(road.to()).add(new Edge(road.from(), road.meters()));
        }
        adjacency.values().forEach(edges -> edges.sort(Comparator.comparing(Edge::to)));
        Map<String, Long> distance = new HashMap<>(); Map<String, String> previous = new HashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingLong(Entry::distance).thenComparing(Entry::id));
        distance.put(from, 0L); queue.add(new Entry(from, 0));
        while (!queue.isEmpty()) {
            Entry current = queue.poll(); if (current.distance() != distance.getOrDefault(current.id(), Long.MAX_VALUE)) continue;
            if (current.id().equals(to)) {
                List<String> path = new ArrayList<>(); for (String id = to; id != null; id = previous.get(id)) path.add(id);
                Collections.reverse(path); return Optional.of(new Result(path, current.distance()));
            }
            for (Edge edge : adjacency.get(current.id())) {
                long next = Math.addExact(current.distance(), edge.meters());
                if (next < distance.getOrDefault(edge.to(), Long.MAX_VALUE)) {
                    distance.put(edge.to(), next); previous.put(edge.to(), current.id()); queue.add(new Entry(edge.to(), next));
                }
            }
        }
        return Optional.empty();
    }
}
