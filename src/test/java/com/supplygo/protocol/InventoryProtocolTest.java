package com.supplygo.protocol;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InventoryProtocolTest {
    String request(String payload) {
        return "{\"version\":1,\"requestId\":\"stock\",\"type\":\"RECEIVE_BATCH\",\"payload\":" + payload + "}";
    }
    @Test void rejectsNonIntegerQuantitiesAndForgedIdentity() throws Exception {
        for (String number : new String[]{"-1", "1.5", "1e2", "\"2\"", "null", "10000000", "true"}) {
            assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(request("{\"productId\":\"APPLE\",\"quantity\":" + number + "}")));
        }
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(request("{\"productId\":\"APPLE\",\"quantity\":2,\"role\":\"ADMIN\"}")));
        assertEquals(MessageType.RECEIVE_BATCH, ProtocolCodec.decodeRequest(request("{\"productId\":\"APPLE\",\"quantity\":2}")).type());
    }
    @Test void rejectsMalformedInventoryReplies() {
        for (String data : new String[]{
                "{\"message\":\"PRODUCTS\",\"products\":[],\"nextOffset\":1.5}",
                "{\"message\":\"PRODUCTS\",\"products\":[{\"id\":\"APPLE\",\"name\":\"Apple\",\"quantity\":-1}],\"nextOffset\":-1}",
                "{\"message\":\"BATCHES\",\"batches\":[{}],\"nextOffset\":-1}"}) {
            assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse("{\"version\":1,\"requestId\":\"stock\",\"status\":\"OK\",\"data\":" + data + "}"));
        }
    }
}
