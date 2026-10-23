package com.supplygo.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import com.supplygo.model.*;

class BusinessProfileProtocolTest {
    @Test void forgedIdentityWrongTypesAndUnexpectedFieldsAreRejected() throws Exception {
        JsonObject payload = new JsonObject(); payload.addProperty("fullName", "Name"); payload.addProperty("businessName", "Business");
        payload.addProperty("contactNumber", "0771234567"); payload.addProperty("location", "Matara");
        assertEquals(MessageType.UPDATE_BUSINESS_PROFILE, ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.UPDATE_BUSINESS_PROFILE, payload))).type());
        payload.addProperty("username", "victim");
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.UPDATE_BUSINESS_PROFILE, payload))));
        payload.remove("username"); payload.addProperty("role", "ADMIN");
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.UPDATE_BUSINESS_PROFILE, payload))));
        payload.remove("role"); payload.addProperty("contactNumber", 771234567);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.UPDATE_BUSINESS_PROFILE, payload))));
        for (String offset : new String[]{"-1", "1.5", "1e1", "\"0\""}) {
            JsonObject page = JsonParser.parseString("{\"offset\":" + offset + "}").getAsJsonObject();
            assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.LIST_SELLERS, page))));
        }
    }
    @Test void fullPageFitsFrameAndMalformedRowsAreRejected() throws Exception {
        JsonArray rows = new JsonArray();
        for (int i = 0; i < 10; i++) rows.add(BusinessProfileData.encode(new BusinessProfile("account" + "a".repeat(24) + i, Role.SUPPLIER,
                "名".repeat(30), "商".repeat(50), "+94 771234567", "町".repeat(40), "2026-10-23T00:00:00.123456789Z", "2026-10-23T00:00:00.123456789Z")));
        JsonObject page = new JsonObject(); page.add("profiles", rows); page.addProperty("nextOffset", 10);
        String encoded = ProtocolCodec.encode(Response.ok("r".repeat(64), "BUSINESS_PROFILES", page));
        var output = new ByteArrayOutputStream(); FrameIO.write(output, encoded); assertTrue(output.size() <= 8193);
        assertEquals(10, ProtocolCodec.decodeResponse(encoded).attributes().getAsJsonArray("profiles").size());
        rows.add(rows.get(0)); assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("id", "BUSINESS_PROFILES", page))));
        rows.remove(10); rows.get(0).getAsJsonObject().addProperty("role", "ADMIN");
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("id", "BUSINESS_PROFILES", page))));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("id", "BUSINESS_PROFILE"))));
    }
}
