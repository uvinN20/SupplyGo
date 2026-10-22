package com.supplygo.protocol;

import java.util.Objects;
import java.util.UUID;

public record Request(String requestId, MessageType type) {
    public Request {
        Objects.requireNonNull(type, "type");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalArgumentException("Invalid request ID");
        }
    }

    public static Request create(MessageType type) {
        return new Request(UUID.randomUUID().toString(), type);
    }
}
