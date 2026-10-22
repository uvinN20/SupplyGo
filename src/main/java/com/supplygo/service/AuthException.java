package com.supplygo.service;

public final class AuthException extends Exception {
    private final String code;
    public AuthException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
