package com.supplygo.protocol;

import java.util.*;
import com.google.gson.*;
import com.supplygo.model.RouteGraph;
import com.supplygo.algorithm.ShortestRouteFinder;

public final class RouteData {
    private RouteData() { }
    public static JsonObject encode(ShortestRouteFinder.Result result) {
        JsonObject data = new JsonObject(); JsonArray path = new JsonArray(); result.path().forEach(path::add);
        data.add("path", path); data.addProperty("meters", result.meters()); return data;
    }
    public static void validate(JsonObject data, String message) {
        if (message.equals("ROUTE")) {
            if (!data.keySet().equals(Set.of("path", "meters")) || data.getAsJsonArray("path").isEmpty() || data.getAsJsonArray("path").size() > 100) throw new IllegalArgumentException();
            Set<String> ids = new HashSet<>();
            for (JsonElement id : data.getAsJsonArray("path")) if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString() || !RouteGraph.validId(id.getAsString()) || !ids.add(id.getAsString())) throw new IllegalArgumentException();
            long meters = number(data, "meters", 0, 99_000_000_000L);
            if ((ids.size() == 1) != (meters == 0)) throw new IllegalArgumentException();
        } else {
            boolean roads = message.equals("ROUTE_ROADS"); String key = roads ? "roads" : "locations";
            if (!data.keySet().equals(Set.of(key, "nextOffset")) || data.getAsJsonArray(key).size() > 10) throw new IllegalArgumentException();
            number(data, "nextOffset", -1, 1000);
            for (JsonElement element : data.getAsJsonArray(key)) {
                JsonObject row = element.getAsJsonObject();
                if (!row.keySet().equals(roads ? Set.of("from", "to", "meters") : Set.of("id", "name"))) throw new IllegalArgumentException();
                if (roads) new RouteGraph.Road(text(row, "from"), text(row, "to"), number(row, "meters", 1, 1_000_000_000));
                else new RouteGraph.Location(text(row, "id"), text(row, "name"));
            }
        }
    }
    private static String text(JsonObject row, String key) {
        if (!(row.get(key) instanceof JsonPrimitive p) || !p.isString()) throw new IllegalArgumentException(); return p.getAsString();
    }
    private static long number(JsonObject row, String key, long min, long max) {
        if (!(row.get(key) instanceof JsonPrimitive p) || !p.isNumber() || !p.getAsString().matches("-?[0-9]{1,19}")) throw new IllegalArgumentException();
        long value = Long.parseLong(p.getAsString()); if (value < min || value > max) throw new IllegalArgumentException(); return value;
    }
}
