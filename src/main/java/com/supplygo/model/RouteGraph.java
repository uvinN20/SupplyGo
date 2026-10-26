package com.supplygo.model;

import java.util.*;

/** Small undirected, positive-distance graph; immutable snapshots. */
public record RouteGraph(int version, List<Location> locations, List<Road> roads) {
    public record Location(String id, String name) {
        public Location {
            if (!validId(id) || name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 64
                    || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c >= 0xD800 && c <= 0xDFFF))
                throw new IllegalArgumentException("Location needs an uppercase ID (1–24) and printable name (1–64)");
        }
    }
    public record Road(String from, String to, long meters) {
        public Road {
            if (!validId(from) || !validId(to) || from.compareTo(to) >= 0 || meters < 1 || meters > 1_000_000_000L)
                throw new IllegalArgumentException("Invalid road endpoints or distance (1–1000000000 meters)");
        }
        public static Road between(String from, String to, long meters) {
            if (!validId(from) || !validId(to)) throw new IllegalArgumentException("Invalid location ID");
            return from.compareTo(to) < 0 ? new Road(from, to, meters) : new Road(to, from, meters);
        }
    }
    public RouteGraph {
        locations = List.copyOf(locations); roads = List.copyOf(roads);
        if (version != 1 || locations.size() > 100 || roads.size() > 1000) throw new IllegalArgumentException("Graph limit: 100 locations and 1000 roads");
        Set<String> ids = new HashSet<>(), pairs = new HashSet<>();
        for (Location location : locations) if (!ids.add(location.id())) throw new IllegalArgumentException("Duplicate location");
        for (Road road : roads) if (!ids.contains(road.from()) || !ids.contains(road.to()) || !pairs.add(road.from() + ":" + road.to()))
            throw new IllegalArgumentException("Invalid road reference or duplicate road");
    }
    public static boolean validId(String id) { return id != null && id.matches("[A-Z0-9_-]{1,24}"); }
}
