package com.supplygo.protocol;

import java.util.Objects;
import java.util.UUID;
import com.google.gson.JsonObject;

public record Request(String requestId, MessageType type, JsonObject payload) {
    public Request {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}")) throw new IllegalArgumentException("Invalid request ID");
        payload = payload.deepCopy();
    }
    public Request(String requestId, MessageType type) { this(requestId, type, new JsonObject()); }
    public static Request create(MessageType type) { return new Request(UUID.randomUUID().toString(), type); }
    public static Request create(MessageType type, JsonObject payload) { return new Request(UUID.randomUUID().toString(), type, payload); }
    @Override public JsonObject payload() { return payload.deepCopy(); }
    @Override public String toString() { return "Request[id=" + requestId + ", type=" + type + ", payload=redacted]"; }
}
