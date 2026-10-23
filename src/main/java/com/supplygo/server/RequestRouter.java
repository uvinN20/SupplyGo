package com.supplygo.server;

import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.supplygo.model.OrderStatus;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import com.supplygo.protocol.MessageType;
import com.supplygo.protocol.OrderData;
import com.supplygo.protocol.Request;
import com.supplygo.protocol.Response;
import com.supplygo.service.AuthException;
import com.supplygo.service.AuthService;
import com.supplygo.service.InventoryService;
import com.supplygo.service.OrderService;
import com.supplygo.service.UserSession;

/** Per-connection dispatch. Clients cannot supply a trusted identity or session role. */
final class RequestRouter {
    private final AuthService authentication;
    private final boolean secure;
    private final InventoryService inventory;
    private final OrderService orders;
    private final UserSession session = new UserSession();

    RequestRouter(AuthService authentication, boolean secure, InventoryService inventory,
            OrderService orders) {
        this.authentication = authentication;
        this.secure = secure;
        this.inventory = inventory;
        this.orders = orders;
    }

    Response handle(Request request) {
        String id = request.requestId();
        if (request.type() == MessageType.PING) return Response.ok(id, "PONG");
        if (request.type() == MessageType.QUIT) { session.logout(); return Response.ok(id, "BYE"); }
        if (!secure) return Response.error(id, "TLS_REQUIRED", "Use an encrypted TLS connection for protected operations");
        if (authentication == null) return Response.error(id, "AUTH_UNAVAILABLE", "Authentication is not configured");
        try {
            return switch (request.type()) {
                case LOGIN, REGISTER -> {
                    JsonObject data = request.payload();
                    char[] password = data.get("password").getAsString().toCharArray();
                    try {
                        if (request.type() == MessageType.LOGIN) {
                            session.logout();
                            UserProfile profile = authentication.login(data.get("username").getAsString(), password);
                            session.login(profile);
                            yield profile(id, "SIGNED_IN", profile);
                        }
                        UserProfile created = authentication.register(data.get("username").getAsString(), password,
                                Role.valueOf(data.get("role").getAsString()));
                        yield profile(id, "REGISTERED", created);
                    } finally { Arrays.fill(password, '\0'); }
                }
                case LIST_PRODUCTS, LIST_BATCHES, CREATE_PRODUCT, RECEIVE_BATCH, WITHDRAW_STOCK -> {
                    UserProfile actor = session.requireUser();
                    if (inventory == null) throw new AuthException("INVENTORY_UNAVAILABLE", "Inventory is not configured");
                    JsonObject data = request.payload();
                    switch (request.type()) {
                        case CREATE_PRODUCT -> inventory.createProduct(actor, data.get("productId").getAsString(), data.get("name").getAsString());
                        case RECEIVE_BATCH -> inventory.receive(actor, data.get("productId").getAsString(), data.get("quantity").getAsInt());
                        case WITHDRAW_STOCK -> inventory.withdraw(actor, data.get("productId").getAsString(), data.get("quantity").getAsInt());
                        default -> { }
                    }
                    if (request.type() == MessageType.LIST_PRODUCTS || request.type() == MessageType.LIST_BATCHES) {
                        boolean batches = request.type() == MessageType.LIST_BATCHES;
                        yield Response.ok(id, batches ? "BATCHES" : "PRODUCTS", inventory.list(actor, batches, data.get("offset").getAsInt()));
                    }
                    yield Response.ok(id, "INVENTORY_UPDATED");
                }
                case CREATE_ORDER, LIST_ORDERS, GET_ORDER, APPROVE_ORDER, FULFIL_ORDER, CANCEL_ORDER -> {
                    UserProfile actor = session.requireUser();
                    if (orders == null) throw new AuthException("ORDERS_UNAVAILABLE", "Orders are not configured");
                    JsonObject data = request.payload();
                    JsonObject attributes = new JsonObject();
                    if (request.type() == MessageType.LIST_ORDERS) {
                        var page = orders.list(actor, data.get("offset").getAsInt());
                        var rows = new JsonArray();
                        page.orders().forEach(order -> rows.add(OrderData.encode(order)));
                        attributes.add("orders", rows); attributes.addProperty("nextOffset", page.nextOffset());
                        yield Response.ok(id, "ORDERS", attributes);
                    }
                    var order = switch (request.type()) {
                        case CREATE_ORDER -> orders.create(actor, data.get("clientOrderId").getAsString(),
                                data.get("productId").getAsString(), data.get("quantity").getAsInt());
                        case GET_ORDER -> orders.get(actor, data.get("orderId").getAsLong());
                        case APPROVE_ORDER -> orders.transition(actor, data.get("orderId").getAsLong(), OrderStatus.APPROVED);
                        case FULFIL_ORDER -> orders.transition(actor, data.get("orderId").getAsLong(), OrderStatus.FULFILLED);
                        case CANCEL_ORDER -> orders.transition(actor, data.get("orderId").getAsLong(), OrderStatus.CANCELLED);
                        default -> throw new IllegalStateException("Unhandled order operation");
                    };
                    attributes.add("order", OrderData.encode(order));
                    yield Response.ok(id, "ORDER", attributes);
                }
                case GET_PROFILE -> profile(id, "PROFILE", session.requireUser());
                case LOGOUT -> { session.logout(); yield Response.ok(id, "SIGNED_OUT"); }
                case ADMIN_STATS -> {
                    session.requireRole(Role.ADMIN);
                    JsonObject attributes = new JsonObject();
                    attributes.addProperty("userCount", authentication.userCount());
                    yield Response.ok(id, "ADMIN_STATS", attributes);
                }
                default -> throw new IllegalStateException("Unhandled request type");
            };
        } catch (AuthException exception) {
            return Response.error(id, exception.code(), exception.getMessage());
        }
    }

    private static Response profile(String id, String message, UserProfile profile) {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("username", profile.username());
        attributes.addProperty("role", profile.role().name());
        return Response.ok(id, message, attributes);
    }
}
