package com.supplygo.protocol;

import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import com.google.gson.JsonObject;

/** Issued only over authenticated TLS; never log these short-lived secrets. */
public record ChatCredentials(String ticket, String key, int port, String expiresAt) {
    public ChatCredentials {
        if (ticket == null || !ticket.matches("[a-f0-9]{32}") || key == null || !key.matches("[A-Za-z0-9_-]{43}")
                || Base64.getUrlDecoder().decode(key).length != 32 || port < 1 || port > 65535)
            throw new IllegalArgumentException("Invalid chat credentials");
        Instant.parse(expiresAt);
    }
    public JsonObject encode() {
        JsonObject value = new JsonObject(); value.addProperty("ticket", ticket); value.addProperty("key", key);
        value.addProperty("port", port); value.addProperty("expiresAt", expiresAt); return value;
    }
    public static ChatCredentials decode(JsonObject value) {
        if (!value.keySet().equals(Set.of("ticket", "key", "port", "expiresAt"))) throw new IllegalArgumentException("Invalid chat grant");
        long port = UdpChatProtocol.integer(value, "port");
        if (port > 65535) throw new IllegalArgumentException("Invalid chat port");
        return new ChatCredentials(UdpChatProtocol.string(value, "ticket"), UdpChatProtocol.string(value, "key"), (int) port,
                UdpChatProtocol.string(value, "expiresAt"));
    }
    @Override public String toString() { return "ChatCredentials[redacted]"; }
}
