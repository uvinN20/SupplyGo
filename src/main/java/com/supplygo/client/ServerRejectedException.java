package com.supplygo.client;

import java.io.IOException;

/** A correlated application error. The socket remains usable for another request. */
public final class ServerRejectedException extends IOException {
    private final String code;
    public ServerRejectedException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
