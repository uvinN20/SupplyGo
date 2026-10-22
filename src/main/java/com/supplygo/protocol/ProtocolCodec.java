package com.supplygo.protocol;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Version 1 has deliberately small, explicitly validated envelopes. */
public final class ProtocolCodec {
    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setStrictness(Strictness.STRICT).create();

    private ProtocolCodec() { }

    public static String encode(Request request) {
        JsonObject json = envelope(request.requestId());
        json.addProperty("type", request.type().name());
        json.add("payload", new JsonObject());
        return GSON.toJson(json);
    }

    public static String encode(Response response) {
        JsonObject json = envelope(response.requestId());
        json.addProperty("status", response.status().name());
        JsonObject detail = new JsonObject();
        detail.addProperty("message", response.message());
        if (response.status() == Response.Status.OK) {
            json.add("data", detail);
        } else {
            detail.addProperty("code", response.errorCode());
            json.add("error", detail);
        }
        return GSON.toJson(json);
    }

    public static Request decodeRequest(String frame) throws ProtocolException {
        JsonObject json = parse(frame);
        String id = requestId(json, false);
        version(json, id);
        if (!json.keySet().equals(Set.of("version", "requestId", "type", "payload"))
                || !json.get("payload").isJsonObject() || !json.getAsJsonObject("payload").isEmpty()) {
            throw invalid(id, "Expected version, requestId, type and an empty payload object");
        }
        String type = string(json, "type", id);
        try {
            return new Request(id, MessageType.valueOf(type));
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("UNKNOWN_TYPE", id, "Supported message types are PING and QUIT");
        }
    }

    public static Response decodeResponse(String frame) throws ProtocolException {
        JsonObject json = parse(frame);
        String id = requestId(json, true);
        version(json, id);
        String status = string(json, "status", id);
        if ("OK".equals(status) && id != null
                && json.keySet().equals(Set.of("version", "requestId", "status", "data"))
                && json.get("data").isJsonObject()) {
            JsonObject data = json.getAsJsonObject("data");
            if (data.keySet().equals(Set.of("message"))) {
                return Response.ok(id, string(data, "message", id));
            }
        } else if ("ERROR".equals(status)
                && json.keySet().equals(Set.of("version", "requestId", "status", "error"))
                && json.get("error").isJsonObject()) {
            JsonObject error = json.getAsJsonObject("error");
            if (error.keySet().equals(Set.of("code", "message"))) {
                return Response.error(id, string(error, "code", id), string(error, "message", id));
            }
        }
        throw invalid(id, "Invalid response envelope");
    }

    private static JsonObject envelope(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("requestId", id);
        return json;
    }

    private static JsonObject parse(String frame) throws ProtocolException {
        try (JsonReader reader = new JsonReader(new StringReader(frame))) {
            reader.setStrictness(Strictness.STRICT);
            reader.setNestingLimit(16);
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null || reader.peek() != JsonToken.END_DOCUMENT) {
                throw invalid(null, "Expected exactly one JSON object");
            }
            return json;
        } catch (JsonParseException | IllegalStateException | IOException exception) {
            throw invalid(null, "Expected one valid JSON object with at most 16 nesting levels");
        }
    }

    private static void version(JsonObject json, String id) throws ProtocolException {
        if (!json.has("version") || !json.get("version").isJsonPrimitive()
                || !json.getAsJsonPrimitive("version").isNumber()) {
            throw invalid(id, "version must be the integer 1");
        }
        String version = json.get("version").getAsString();
        if (!version.matches("[0-9]+")) {
            throw invalid(id, "version must be an integer");
        }
        if (!"1".equals(version)) {
            throw new ProtocolException("UNSUPPORTED_VERSION", id, "Only protocol version 1 is supported");
        }
    }

    private static String requestId(JsonObject json, boolean nullable) throws ProtocolException {
        if (nullable && json.has("requestId") && json.get("requestId").isJsonNull()) {
            return null;
        }
        String id = string(json, "requestId", null);
        if (!id.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw invalid(null, "requestId must contain 1-64 letters, digits, dots, underscores, colons or hyphens");
        }
        return id;
    }

    private static String string(JsonObject json, String key, String id) throws ProtocolException {
        if (json.has(key) && json.get(key) instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
        }
        throw invalid(id, key + " must be a string");
    }

    private static ProtocolException invalid(String id, String message) {
        return new ProtocolException("INVALID_MESSAGE", id, message);
    }
}
