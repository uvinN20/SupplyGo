package com.supplygo.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;

class UdpChatProtocolTest {
    ChatCredentials grant() { return new ChatCredentials("a".repeat(32), Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]), 7071, Instant.now().plusSeconds(60).toString()); }
    @Test void signaturesBindDirectionSequenceAndPayloadAndMaximumTextFitsPacket() throws Exception {
        var grant = grant(); JsonObject body = UdpChatProtocol.command("MESSAGE");
        body.addProperty("text", "\"".repeat(240)); body.addProperty("sender", "s".repeat(32)); body.addProperty("role", "PRODUCTION_MANAGER"); body.addProperty("time", Instant.now().toString());
        byte[] encoded = UdpChatProtocol.encode(grant, Long.MAX_VALUE, body, true); assertTrue(encoded.length <= 1200);
        var packet = UdpChatProtocol.parse(encoded, encoded.length); assertEquals(body, UdpChatProtocol.verify(packet, grant, true));
        assertThrows(IOException.class, () -> UdpChatProtocol.verify(packet, grant, false));
        assertThrows(IOException.class, () -> UdpChatProtocol.verify(new UdpChatProtocol.Packet(packet.ticket(), 1, packet.payload(), packet.signature()), grant, true));
        assertThrows(IOException.class, () -> UdpChatProtocol.verify(new UdpChatProtocol.Packet(packet.ticket(), packet.sequence(), "e30", packet.signature()), grant, true));
    }
    @Test void strictSchemasDuplicateKeysInvalidUtf8AndTextLimitsAreEnforced() throws Exception {
        byte[] duplicate = "{\"version\":1,\"version\":1}".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> UdpChatProtocol.parse(duplicate, duplicate.length));
        assertThrows(IOException.class, () -> UdpChatProtocol.parse(new byte[]{(byte)0xff}, 1));
        assertThrows(IOException.class, () -> UdpChatProtocol.parse(new byte[1201], 1201));
        for (String invalid : new String[]{"", " ", "a\nb", "x".repeat(241), "名".repeat(81), "\uD800"})
            assertThrows(IllegalArgumentException.class, () -> UdpChatProtocol.text(invalid));
        assertEquals("名".repeat(80), UdpChatProtocol.text("名".repeat(80)));
    }
    @Test void chatGrantUsesStrictTlsEnvelopeAndRedactedDiagnostics() throws Exception {
        var grant = grant(); var response = Response.ok("id", "CHAT_GRANT", grant.encode());
        assertEquals(grant, ChatCredentials.decode(ProtocolCodec.decodeResponse(ProtocolCodec.encode(response)).attributes()));
        assertFalse(grant.toString().contains(grant.key())); assertFalse(response.toString().contains(grant.key()));
        JsonObject extra = grant.encode(); extra.addProperty("extra", true);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("id", "CHAT_GRANT", extra))));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("id", "CHAT_GRANT"))));
        JsonObject payload = new JsonObject(); payload.addProperty("username", "admin");
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.OPEN_CHAT, payload))));
    }
}
