package com.supplygo.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import com.supplygo.model.*;

class OrderProtocolTest {
    String request(String type, String payload) {
        return "{\"version\":1,\"requestId\":\"order\",\"type\":\"" + type + "\",\"payload\":" + payload + "}";
    }
    SupplyOrder order() {
        String now = Instant.now().toString();
        return new SupplyOrder(Long.MAX_VALUE - 1, UUID.randomUUID().toString(), "s".repeat(32), "P".repeat(24),
                1000000, OrderStatus.APPROVED, now, now, "m".repeat(32));
    }
    @Test void forgedSellerStatusAndInvalidIdsAreRejected() throws Exception {
        JsonObject payload = new JsonObject(); payload.addProperty("clientOrderId", UUID.randomUUID().toString());
        payload.addProperty("productId", "APPLE"); payload.addProperty("quantity", 2);
        assertEquals(MessageType.CREATE_ORDER, ProtocolCodec.decodeRequest(request("CREATE_ORDER", payload.toString())).type());
        for (String key : new String[]{"sellerUsername", "status", "role", "createdAt"}) {
            JsonObject forged = payload.deepCopy(); forged.addProperty(key, "forged");
            assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(request("CREATE_ORDER", forged.toString())));
        }
        for (String id : new String[]{"0", "-1", "1.5", "1e2", "\"1\"", "null", "9223372036854775808"})
            assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeRequest(request("GET_ORDER", "{\"orderId\":" + id + "}")));
        assertEquals(Long.MAX_VALUE, ProtocolCodec.decodeRequest(request("GET_ORDER", "{\"orderId\":9223372036854775807}")).payload().get("orderId").getAsLong());
    }
    @Test void boundedOrderPagesRoundTripWithinFrameLimit() throws Exception {
        JsonObject page = new JsonObject(); JsonArray rows = new JsonArray();
        for (int i = 0; i < 10; i++) rows.add(OrderData.encode(order()));
        page.add("orders", rows); page.addProperty("nextOffset", 10);
        String encoded = ProtocolCodec.encode(Response.ok("order", "ORDERS", page));
        FrameIO.write(new ByteArrayOutputStream(), encoded);
        assertEquals(page, ProtocolCodec.decodeResponse(encoded).attributes());
        rows.add(OrderData.encode(order()));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("order", "ORDERS", page))));
    }
    @Test void rejectsInvalidOrderRecordsAndStockTotals() throws Exception {
        for (String field : new String[]{"sellerUsername", "status", "quantity", "createdAt", "clientOrderId"}) {
            var broken = OrderData.encode(order()); broken.addProperty(field, "invalid value!");
            JsonObject data = new JsonObject(); data.add("order", broken);
            assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("order", "ORDER", data))));
        }
        JsonObject data = new JsonObject(); data.add("order", OrderData.encode(order()));
        assertNotNull(ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("order", "ORDER", data))));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("order", "ORDER"))));
        JsonObject product = new JsonObject(); product.addProperty("id", "APPLE"); product.addProperty("name", "Apple");
        product.addProperty("quantity", 10); product.addProperty("onHand", 10); product.addProperty("reserved", 5);
        JsonArray products = new JsonArray(); products.add(product); JsonObject page = new JsonObject(); page.add("products", products); page.addProperty("nextOffset", -1);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("order", "PRODUCTS", page))));
    }
}
