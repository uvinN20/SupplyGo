package com.supplygo.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.google.gson.*;

/** Versioned, bounded datagrams. Direction-bound HMAC authenticates plaintext; it does not encrypt it. */
public final class UdpChatProtocol {
    public static final int MAX_PACKET = 1200;
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private UdpChatProtocol() { }
    public record Packet(String ticket, long sequence, String payload, String signature) { }
    public static byte[] encode(ChatCredentials grant, long sequence, JsonObject payload, boolean fromServer) {
        String body = ENCODER.encodeToString(JSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
        String signature = ENCODER.encodeToString(mac(grant.key(), signed(grant.ticket(), sequence, body, fromServer)));
        JsonObject packet = new JsonObject(); packet.addProperty("version", 1); packet.addProperty("ticket", grant.ticket());
        packet.addProperty("sequence", sequence); packet.addProperty("payload", body); packet.addProperty("mac", signature);
        byte[] bytes = JSON.toJson(packet).getBytes(StandardCharsets.UTF_8);
        if (sequence < 1 || bytes.length > MAX_PACKET) throw new IllegalArgumentException("Chat datagram exceeds limits");
        return bytes;
    }
    public static Packet parse(byte[] bytes, int length) throws IOException {
        if (length < 1 || length > MAX_PACKET) throw new IOException("Invalid datagram size");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, length)).toString();
            JsonObject packet = ProtocolCodec.parse(text);
            if (!packet.keySet().equals(Set.of("version", "ticket", "sequence", "payload", "mac")) || integer(packet, "version") != 1)
                throw new IllegalArgumentException();
            String ticket = string(packet, "ticket"), payload = string(packet, "payload"), signature = string(packet, "mac");
            if (!ticket.matches("[a-f0-9]{32}") || !payload.matches("[A-Za-z0-9_-]{1,1000}") || !signature.matches("[A-Za-z0-9_-]{43}"))
                throw new IllegalArgumentException();
            return new Packet(ticket, integer(packet, "sequence"), payload, signature);
        } catch (RuntimeException exception) { throw new IOException("Invalid chat packet"); }
    }
    public static JsonObject verify(Packet packet, ChatCredentials grant, boolean fromServer) throws IOException {
        try {
            if (!packet.ticket().equals(grant.ticket()) || !MessageDigest.isEqual(DECODER.decode(packet.signature()),
                    mac(grant.key(), signed(packet.ticket(), packet.sequence(), packet.payload(), fromServer)))) throw new IOException("Invalid signature");
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(DECODER.decode(packet.payload()))).toString();
            return ProtocolCodec.parse(json);
        } catch (RuntimeException exception) { throw new IOException("Invalid chat payload"); }
    }
    private static byte[] signed(String ticket, long sequence, String payload, boolean fromServer) {
        return ((fromServer ? "S" : "C") + "|1|" + ticket + "|" + sequence + "|" + payload).getBytes(StandardCharsets.US_ASCII);
    }
    private static byte[] mac(String key, byte[] bytes) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(DECODER.decode(key), "HmacSHA256")); return mac.doFinal(bytes); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("HMAC unavailable", exception); }
    }
    public static String string(JsonObject value, String key) {
        if (!(value.get(key) instanceof JsonPrimitive p) || !p.isString()) throw new IllegalArgumentException("Invalid chat field");
        return p.getAsString();
    }
    public static long integer(JsonObject value, String key) {
        if (!(value.get(key) instanceof JsonPrimitive p) || !p.isNumber() || !p.getAsString().matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid chat integer");
        return Long.parseLong(p.getAsString());
    }
    public static String text(String value) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > 240
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c >= 0xD800 && c <= 0xDFFF))
            throw new IllegalArgumentException("Enter a single-line message of at most 240 UTF-8 bytes");
        return value;
    }
    public static JsonObject command(String type) { JsonObject body = new JsonObject(); body.addProperty("type", type); return body; }
}
