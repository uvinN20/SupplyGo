package com.supplygo.protocol;

import java.util.Set;
import com.google.gson.*;
import com.supplygo.model.OrderStatus;
import com.supplygo.model.SupplyOrder;

/** Explicit order wire schema; credentials and client-supplied roles are never included. */
public final class OrderData {
    private static final Set<String> KEYS = Set.of("id", "clientOrderId", "sellerUsername", "productId", "quantity",
            "status", "createdAt", "updatedAt", "updatedBy");
    private OrderData() { }
    public static JsonObject encode(SupplyOrder order) {
        JsonObject data = new JsonObject();
        data.addProperty("id", order.id()); data.addProperty("clientOrderId", order.clientOrderId());
        data.addProperty("sellerUsername", order.sellerUsername()); data.addProperty("productId", order.productId());
        data.addProperty("quantity", order.quantity()); data.addProperty("status", order.status().name());
        data.addProperty("createdAt", order.createdAt()); data.addProperty("updatedAt", order.updatedAt());
        data.addProperty("updatedBy", order.updatedBy());
        return data;
    }
    public static SupplyOrder decode(JsonElement element, String requestId) throws ProtocolException {
        try {
            if (element == null || !element.isJsonObject()) throw new IllegalArgumentException();
            JsonObject data = element.getAsJsonObject();
            if (!data.keySet().equals(KEYS)) throw new IllegalArgumentException();
            for (String key : KEYS) {
                if (key.equals("id") || key.equals("quantity")) continue;
                if (!(data.get(key) instanceof JsonPrimitive value) || !value.isString() || value.getAsString().length() > 64)
                    throw new IllegalArgumentException();
            }
            return new SupplyOrder(number(data, "id", Long.MAX_VALUE), data.get("clientOrderId").getAsString(),
                    data.get("sellerUsername").getAsString(), data.get("productId").getAsString(),
                    (int) number(data, "quantity", 1_000_000), OrderStatus.valueOf(data.get("status").getAsString()),
                    data.get("createdAt").getAsString(), data.get("updatedAt").getAsString(), data.get("updatedBy").getAsString());
        } catch (RuntimeException exception) {
            throw new ProtocolException("INVALID_MESSAGE", requestId, "Invalid order response");
        }
    }
    private static long number(JsonObject object, String key, long maximum) {
        if (!(object.get(key) instanceof JsonPrimitive value) || !value.isNumber() || !value.getAsString().matches("[0-9]{1,19}"))
            throw new IllegalArgumentException();
        long number = Long.parseLong(value.getAsString());
        if (number < 1 || number > maximum) throw new IllegalArgumentException();
        return number;
    }
}
