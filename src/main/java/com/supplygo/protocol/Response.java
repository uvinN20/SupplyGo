package com.supplygo.protocol;

/** An error before a valid request ID is available has a null requestId. */
public record Response(String requestId, Status status, String message, String errorCode) {
    public enum Status { OK, ERROR }

    public static Response ok(String requestId, String message) {
        return new Response(requestId, Status.OK, message, null);
    }

    public static Response error(String requestId, String code, String message) {
        return new Response(requestId, Status.ERROR, message, code);
    }
}
