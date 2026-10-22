package com.supplygo.protocol;

import com.google.gson.JsonObject;

/** Public reply data never includes credentials or password hashes. */
public record Response(String requestId, Status status, String message, String errorCode, JsonObject attributes) {
    public enum Status { OK, ERROR }
    public Response { attributes = attributes.deepCopy(); }
    public Response(String id, Status status, String message, String code) { this(id, status, message, code, new JsonObject()); }
    @Override public JsonObject attributes() { return attributes.deepCopy(); }
    public static Response ok(String id, String message) { return new Response(id, Status.OK, message, null); }
    public static Response ok(String id, String message, JsonObject attributes) { return new Response(id, Status.OK, message, null, attributes); }
    public static Response error(String id, String code, String message) { return new Response(id, Status.ERROR, message, code); }
}
