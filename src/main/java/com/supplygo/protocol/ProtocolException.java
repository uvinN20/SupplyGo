package com.supplygo.protocol;

import java.io.IOException;

public final class ProtocolException extends IOException {
    private final String code;
    private final String requestId;

    public ProtocolException(String code, String requestId, String message) {
        super(message);
        this.code = code;
        this.requestId = requestId;
    }

    public String code() { return code; }
    public String requestId() { return requestId; }
}
