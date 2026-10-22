package com.supplygo.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolCodecTest {
    @Test
    void roundTripsRequestsAndResponsesIncludingUncorrelatedErrors() throws Exception {
        Request ping = new Request("test-1", MessageType.PING);
        assertEquals(ping, ProtocolCodec.decodeRequest(ProtocolCodec.encode(ping)));
        Response pong = Response.ok("test-1", "PONG");
        assertEquals(pong, ProtocolCodec.decodeResponse(ProtocolCodec.encode(pong)));
        Response error = Response.error(null, "INVALID_MESSAGE", "Invalid JSON");
        assertEquals(error, ProtocolCodec.decodeResponse(ProtocolCodec.encode(error)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{", "{}{}", "{version:1}",
            "{\"version\":1,\"requestId\":3,\"type\":\"PING\",\"payload\":{}}",
            "{\"version\":\"1\",\"requestId\":\"x\",\"type\":\"PING\",\"payload\":{}}",
            "{\"version\":1.0,\"requestId\":\"x\",\"type\":\"PING\",\"payload\":{}}",
            "{\"version\":1,\"requestId\":\"x\",\"type\":\"PING\",\"payload\":null}",
            "{\"version\":1,\"requestId\":\"x\",\"type\":\"PING\",\"payload\":{\"x\":1}}",
            "{\"version\":1,\"requestId\":\"\",\"type\":\"PING\",\"payload\":{}}"})
    void rejectsInvalidRequests(String frame) {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(frame));
    }

    @Test
    void boundsJsonNestingBeforeConstructingDeepTrees() {
        String frame = "{\"x\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}";
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(frame));
    }

    @Test
    void preservesValidIdOnUnsupportedVersionAndUnknownType() {
        ProtocolException version = assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(
                "{\"version\":2,\"requestId\":\"v2\",\"type\":\"PING\",\"payload\":{}}"));
        assertEquals("UNSUPPORTED_VERSION", version.code());
        assertEquals("v2", version.requestId());
        ProtocolException type = assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(
                "{\"version\":1,\"requestId\":\"x\",\"type\":\"DELETE\",\"payload\":{}}"));
        assertEquals("UNKNOWN_TYPE", type.code());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"version\":1,\"requestId\":\"x\",\"status\":\"OK\",\"data\":{\"message\":3}}",
            "{\"version\":1,\"requestId\":null,\"status\":\"OK\",\"data\":{\"message\":\"PONG\"}}",
            "{\"version\":1,\"requestId\":\"x\",\"status\":\"MAYBE\",\"data\":{}}"})
    void rejectsInvalidResponses(String frame) {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(frame));
    }

    @Test
    void framesRespectUtf8CrLfAndByteLimit() throws Exception {
        byte[] wire = "ආයුබෝවන්\r\nnext\n".getBytes(StandardCharsets.UTF_8);
        var input = new ByteArrayInputStream(wire);
        assertEquals("ආයුබෝවන්", FrameIO.read(input));
        assertEquals("next", FrameIO.read(input));
        assertNull(FrameIO.read(input));
        var output = new ByteArrayOutputStream();
        FrameIO.write(output, "x".repeat(FrameIO.MAX_FRAME_BYTES));
        assertEquals(FrameIO.MAX_FRAME_BYTES, FrameIO.read(new ByteArrayInputStream(output.toByteArray())).length());
        assertThrows(ProtocolException.class, () -> FrameIO.write(output, "ආ".repeat(3000)));
        assertThrows(ProtocolException.class, () -> FrameIO.write(output, "line\ninjection"));
    }

    @Test
    void rejectsInvalidUtf8TruncatedAndOversizedFrames() {
        assertThrows(ProtocolException.class, () -> FrameIO.read(new ByteArrayInputStream(new byte[]{(byte) 0xff, 10})));
        assertThrows(ProtocolException.class, () -> FrameIO.read(new ByteArrayInputStream(new byte[]{'x'})));
        assertThrows(ProtocolException.class, () -> FrameIO.read(new ByteArrayInputStream(
                ("x".repeat(FrameIO.MAX_FRAME_BYTES + 1) + "\n").getBytes(StandardCharsets.UTF_8))));
    }
    @Test
    void rejectsDuplicateKeysAndRedactsCredentialPayloads() throws Exception {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(
                "{\"version\":1,\"version\":2,\"requestId\":\"x\",\"type\":\"PING\",\"payload\":{}}"));
        var payload = new com.google.gson.JsonObject();
        payload.addProperty("username", "supplier");
        payload.addProperty("password", "private password");
        Request request = Request.create(MessageType.LOGIN, payload);
        assertFalse(request.toString().contains("private password"));
        payload.addProperty("role", "ADMIN");
        assertFalse(request.payload().has("role"));
        assertEquals(request, ProtocolCodec.decodeRequest(ProtocolCodec.encode(request)));
    }
}
