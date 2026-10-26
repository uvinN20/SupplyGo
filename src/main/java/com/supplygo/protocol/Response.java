package com.supplygo.protocol;

import com.google.gson.JsonObject;

/** Reply data may include short-lived chat credentials over TLS; diagnostic output is redacted. */
public record Response(String requestId, Status status, String message, String errorCode, JsonObject attributes) {
    @Override public String toString() { return "Response[id=" + requestId + ", status=" + status + ", message=" + message + ", attributes=redacted]"; }
    public enum Status { OK, ERROR }
    public Response { attributes = attributes.deepCopy(); }
    public Response(String id, Status status, String message, String code) { this(id, status, message, code, new JsonObject()); }
    @Override public JsonObject attributes() { return attributes.deepCopy(); }
    public static Response ok(String id, String message) { return new Response(id, Status.OK, message, null); }
    public static Response ok(String id, String message, JsonObject attributes) { return new Response(id, Status.OK, message, null, attributes); }
    public static Response error(String id, String code, String message) { return new Response(id, Status.ERROR, message, code); }
}
