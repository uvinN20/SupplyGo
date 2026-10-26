package com.supplygo.service;

import java.io.IOException;
import java.util.*;
import com.google.gson.*;
import com.supplygo.model.*;
import com.supplygo.storage.RouteRepository;
import com.supplygo.algorithm.ShortestRouteFinder;

public final class RouteService {
    private final RouteRepository repository;
    public RouteService(RouteRepository repository) { this.repository = repository; }
    private static void user(UserProfile actor, boolean write) throws AuthException {
        if (actor == null) throw new AuthException("AUTH_REQUIRED", "Sign in first");
        if (write && actor.role() != Role.ADMIN && actor.role() != Role.PRODUCTION_MANAGER) throw new AuthException("FORBIDDEN", "Only administrators and production managers can edit the road network");
    }
    public void location(UserProfile actor, String id, String name) throws AuthException {
        user(actor, true);
        synchronized (repository) {
            try {
                var old = repository.snapshot(); var rows = new ArrayList<>(old.locations());
                var location = new RouteGraph.Location(id, name); rows.removeIf(l -> l.id().equals(id)); rows.add(location); rows.sort(Comparator.comparing(RouteGraph.Location::id));
                repository.save(new RouteGraph(1, rows, old.roads()));
            } catch (IllegalArgumentException error) { throw new AuthException("INVALID_ROUTE", error.getMessage()); }
            catch (IOException error) { throw new AuthException("STORAGE_ERROR", "Road network could not be saved"); }
        }
    }
    public void road(UserProfile actor, String from, String to, long meters) throws AuthException {
        user(actor, true);
        synchronized (repository) {
            try {
                var old = repository.snapshot(); var road = RouteGraph.Road.between(from, to, meters); var rows = new ArrayList<>(old.roads());
                rows.removeIf(e -> e.from().equals(road.from()) && e.to().equals(road.to())); rows.add(road);
                rows.sort(Comparator.comparing(RouteGraph.Road::from).thenComparing(RouteGraph.Road::to)); repository.save(new RouteGraph(1, old.locations(), rows));
            } catch (IllegalArgumentException error) { throw new AuthException("INVALID_ROUTE", error.getMessage()); }
            catch (IOException error) { throw new AuthException("STORAGE_ERROR", "Road network could not be saved"); }
        }
    }
    public JsonObject page(UserProfile actor, boolean roads, int offset) throws AuthException {
        user(actor, false); if (offset < 0 || offset > 1000) throw new AuthException("INVALID_ROUTE", "Offset must be 0–1000");
        var snapshot = repository.snapshot(); List<?> values = roads ? snapshot.roads() : snapshot.locations();
        int start = Math.min(offset, values.size()), end = Math.min(start + 10, values.size());
        JsonObject data = new JsonObject(); JsonArray rows = new JsonArray();
        for (Object value : values.subList(start, end)) {
            JsonObject row = new JsonObject();
            if (value instanceof RouteGraph.Location l) { row.addProperty("id", l.id()); row.addProperty("name", l.name()); }
            else { var road = (RouteGraph.Road)value; row.addProperty("from", road.from()); row.addProperty("to", road.to()); row.addProperty("meters", road.meters()); }
            rows.add(row);
        }
        data.add(roads ? "roads" : "locations", rows); data.addProperty("nextOffset", end < values.size() ? end : -1); return data;
    }
    public ShortestRouteFinder.Result find(UserProfile actor, String from, String to) throws AuthException {
        user(actor, false);
        try { return ShortestRouteFinder.find(repository.snapshot(), from, to).orElseThrow(() -> new AuthException("ROUTE_NOT_FOUND", "No road connection between these locations")); }
        catch (IllegalArgumentException error) { throw new AuthException("INVALID_ROUTE", error.getMessage()); }
    }
}
