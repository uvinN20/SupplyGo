package com.supplygo.protocol;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.supplygo.model.Role;

/** Versioned envelopes with explicit schemas and rejection of duplicate object keys. */
public final class ProtocolCodec {
    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setStrictness(Strictness.STRICT).create();
    private ProtocolCodec() { }

    public static String encode(Request request) {
        JsonObject json = envelope(request.requestId());
        json.addProperty("type", request.type().name());
        json.add("payload", request.payload());
        return GSON.toJson(json);
    }

    public static String encode(Response response) {
        JsonObject json = envelope(response.requestId());
        json.addProperty("status", response.status().name());
        JsonObject detail = response.attributes();
        detail.addProperty("message", response.message());
        if (response.status() == Response.Status.OK) json.add("data", detail);
        else {
            detail.addProperty("code", response.errorCode());
            json.add("error", detail);
        }
        return GSON.toJson(json);
    }

    public static Request decodeRequest(String frame) throws ProtocolException {
        JsonObject json = parse(frame);
        String id = requestId(json, false);
        version(json, id);
        if (!json.keySet().equals(Set.of("version", "requestId", "type", "payload")) || !json.get("payload").isJsonObject()) {
            throw invalid(id, "Expected version, requestId, type and a payload object");
        }
        MessageType type;
        try { type = MessageType.valueOf(string(json, "type", id)); }
        catch (IllegalArgumentException exception) { throw new ProtocolException("UNKNOWN_TYPE", id, "Unsupported message type"); }
        JsonObject payload = json.getAsJsonObject("payload");
        Set<String> keys = switch (type) {
            case LIST_LOCATIONS, LIST_ROADS -> Set.of("offset");
            case SAVE_LOCATION -> Set.of("locationId", "name");
            case SAVE_ROAD -> Set.of("from", "to", "meters");
            case FIND_ROUTE -> Set.of("from", "to");
            case UPDATE_BUSINESS_PROFILE -> Set.of("fullName", "businessName", "contactNumber", "location");
            case LIST_SUPPLIERS, LIST_SELLERS, LIST_PRODUCTS, LIST_BATCHES, LIST_ORDERS -> Set.of("offset");
            case CREATE_ORDER -> Set.of("clientOrderId", "productId", "quantity");
            case GET_ORDER, APPROVE_ORDER, FULFIL_ORDER, CANCEL_ORDER -> Set.of("orderId");
            case CREATE_PRODUCT -> Set.of("productId", "name");
            case RECEIVE_BATCH, WITHDRAW_STOCK -> Set.of("productId", "quantity");
            case LOGIN -> Set.of("username", "password");
            case REGISTER -> Set.of("username", "password", "role");
            default -> Set.of();
        };
        if (!payload.keySet().equals(keys)) throw invalid(id, "Invalid payload fields for " + type);
        for (String key : keys) {
            if (key.equals("meters")) { integer(payload, key, 1, 1_000_000_000, id); continue; }
            if (key.equals("orderId")) {
                integer(payload, key, 1, Long.MAX_VALUE, id);
                continue;
            }
            if (key.equals("offset") || key.equals("quantity")) {
                if (!(payload.get(key) instanceof JsonPrimitive number) || !number.isNumber()
                        || !number.getAsString().matches("[0-9]{1,7}")) throw invalid(id, key + " must be a nonnegative integer");
                continue;
            }
            String value = string(payload, key, id);
            if (value.length() > 128) throw invalid(id, "Payload string exceeds 128 characters");
        }
        if (type == MessageType.REGISTER) {
            try { Role.valueOf(payload.get("role").getAsString()); }
            catch (IllegalArgumentException exception) { throw invalid(id, "Unknown account role"); }
        }
        return new Request(id, type, payload);
    }

    public static Response decodeResponse(String frame) throws ProtocolException {
        JsonObject json = parse(frame);
        String id = requestId(json, true);
        version(json, id);
        String status = string(json, "status", id);
        if ("OK".equals(status) && id != null
                && json.keySet().equals(Set.of("version", "requestId", "status", "data")) && json.get("data").isJsonObject()) {
            JsonObject data = json.getAsJsonObject("data").deepCopy();
            String message = string(data, "message", id);
            data.remove("message");
            if (message.equals("ORDER")) {
                if (!data.keySet().equals(Set.of("order"))) throw invalid(id, "Expected order data");
                OrderData.decode(data.get("order"), id);
                return Response.ok(id, message, data);
            }
            if (message.equals("ORDERS")) {
                if (!data.keySet().equals(Set.of("orders", "nextOffset")) || !data.get("orders").isJsonArray()
                        || data.getAsJsonArray("orders").size() > 10) throw invalid(id, "Invalid order page");
                integer(data, "nextOffset", -1, 10000, id);
                for (JsonElement order : data.getAsJsonArray("orders")) OrderData.decode(order, id);
                return Response.ok(id, message, data);
            }
            if (message.equals("BUSINESS_PROFILE") || message.equals("BUSINESS_PROFILES")) {
                try {
                    if (message.equals("BUSINESS_PROFILE")) {
                        if (!data.keySet().equals(Set.of("profile"))) throw new IllegalArgumentException();
                        BusinessProfileData.decode(data.get("profile"));
                    } else {
                        if (!data.keySet().equals(Set.of("profiles", "nextOffset")) || !data.get("profiles").isJsonArray()
                                || data.getAsJsonArray("profiles").size() > 10) throw new IllegalArgumentException();
                        integer(data, "nextOffset", -1, 10000, id);
                        for (JsonElement row : data.getAsJsonArray("profiles")) BusinessProfileData.decode(row);
                    }
                } catch (RuntimeException exception) { throw invalid(id, "Invalid business profile response"); }
                return Response.ok(id, message, data);
            }
            if (message.equals("CHAT_GRANT")) {
                try { ChatCredentials.decode(data); }
                catch (RuntimeException exception) { throw invalid(id, "Invalid chat grant"); }
                return Response.ok(id, message, data);
            }
            if (Set.of("ROUTE", "ROUTE_LOCATIONS", "ROUTE_ROADS").contains(message)) {
                try { RouteData.validate(data, message); }
                catch (RuntimeException error) { throw invalid(id, "Invalid route response"); }
                return Response.ok(id, message, data);
            }
            if (data.isEmpty()) return Response.ok(id, message);
            if (message.equals("PRODUCTS") || message.equals("BATCHES")) {
                validateInventoryPage(data, message.equals("BATCHES"), id);
                return Response.ok(id, message, data);
            }
            if (data.keySet().equals(Set.of("username", "role"))) {
                string(data, "username", id);
                try { Role.valueOf(string(data, "role", id)); }
                catch (IllegalArgumentException exception) { throw invalid(id, "Unknown user role"); }
                return Response.ok(id, message, data);
            }
            if (data.keySet().equals(Set.of("userCount")) && data.get("userCount") instanceof JsonPrimitive value
                    && value.isNumber() && value.getAsString().matches("[0-9]{1,5}")) {
                return Response.ok(id, message, data);
            }
        } else if ("ERROR".equals(status)
                && json.keySet().equals(Set.of("version", "requestId", "status", "error")) && json.get("error").isJsonObject()) {
            JsonObject error = json.getAsJsonObject("error");
            if (error.keySet().equals(Set.of("code", "message"))) {
                return Response.error(id, string(error, "code", id), string(error, "message", id));
            }
        }
        throw invalid(id, "Invalid response envelope");
    }

    private static void validateInventoryPage(JsonObject data, boolean batches, String id) throws ProtocolException {
        String key = batches ? "batches" : "products";
        if (!data.keySet().equals(Set.of(key, "nextOffset")) || !data.get(key).isJsonArray()
                || data.getAsJsonArray(key).size() > 10) throw invalid(id, "Invalid inventory page");
        integer(data, "nextOffset", -1, 10000, id);
        for (JsonElement element : data.getAsJsonArray(key)) {
            if (!element.isJsonObject()) throw invalid(id, "Invalid inventory row");
            JsonObject row = element.getAsJsonObject();
            try {
                if (batches) {
                    if (!row.keySet().equals(Set.of("id", "productId", "quantity", "receivedAt", "receivedBy"))) throw invalid(id, "Invalid batch fields");
                    String productId = string(row, "productId", id);
                    new com.supplygo.model.Product(productId, "validation");
                    new com.supplygo.model.FruitBatch(integer(row, "id", 1, Long.MAX_VALUE, id), productId,
                            (int) integer(row, "quantity", 1, 1000000, id), string(row, "receivedAt", id), string(row, "receivedBy", id));
                } else {
                    if (!row.keySet().equals(Set.of("id", "name", "quantity", "onHand", "reserved"))) throw invalid(id, "Invalid product fields");
                    new com.supplygo.model.Product(string(row, "id", id), string(row, "name", id));
                    long available = integer(row, "quantity", 0, 10_000_000_000L, id);
                    long onHand = integer(row, "onHand", 0, 10_000_000_000L, id);
                    long reserved = integer(row, "reserved", 0, 10_000_000_000L, id);
                    if (onHand - reserved != available) throw invalid(id, "Inconsistent stock quantities");
                }
            } catch (RuntimeException exception) { throw invalid(id, "Invalid inventory row values"); }
        }
    }
    private static long integer(JsonObject object, String key, long minimum, long maximum, String id) throws ProtocolException {
        if (!(object.get(key) instanceof JsonPrimitive value) || !value.isNumber() || !value.getAsString().matches("-?[0-9]{1,19}"))
            throw invalid(id, "Invalid integer " + key);
        try {
            long number = Long.parseLong(value.getAsString());
            if (number < minimum || number > maximum) throw invalid(id, "Integer out of range");
            return number;
        } catch (NumberFormatException exception) { throw invalid(id, "Integer out of range"); }
    }

    private static JsonObject envelope(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("requestId", id);
        return json;
    }

    public static JsonObject parse(String frame) throws ProtocolException {
        try (JsonReader reader = new JsonReader(new StringReader(frame))) {
            reader.setStrictness(Strictness.STRICT);
            reader.setNestingLimit(16);
            JsonElement value = readValue(reader);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw invalid(null, "Expected one JSON object");
            return value.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            throw invalid(null, "Expected strict JSON with unique keys and at most 16 nesting levels");
        }
    }

    private static JsonElement readValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw invalid(null, "Duplicate JSON key");
                    object.add(name, readValue(reader));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> GSON.fromJson(reader, JsonElement.class);
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid(null, "Invalid JSON value");
        };
    }

    private static void version(JsonObject json, String id) throws ProtocolException {
        if (!json.has("version") || !(json.get("version") instanceof JsonPrimitive value) || !value.isNumber()
                || !value.getAsString().matches("[0-9]+")) throw invalid(id, "version must be an integer");
        if (!"1".equals(value.getAsString())) throw new ProtocolException("UNSUPPORTED_VERSION", id, "Only version 1 is supported");
    }

    private static String requestId(JsonObject json, boolean nullable) throws ProtocolException {
        if (nullable && json.has("requestId") && json.get("requestId").isJsonNull()) return null;
        String id = string(json, "requestId", null);
        if (!id.matches("[A-Za-z0-9._:-]{1,64}")) throw invalid(null, "Invalid request ID");
        return id;
    }

    private static String string(JsonObject json, String key, String id) throws ProtocolException {
        if (json.has(key) && json.get(key) instanceof JsonPrimitive value && value.isString()) return value.getAsString();
        throw invalid(id, key + " must be a string");
    }
    private static ProtocolException invalid(String id, String message) { return new ProtocolException("INVALID_MESSAGE", id, message); }
}
