package com.supplygo.server;

import java.util.Arrays;
import com.google.gson.JsonArray;
import com.supplygo.protocol.MessageType;
import com.supplygo.service.InventoryService;
import com.google.gson.JsonObject;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import com.supplygo.protocol.Request;
import com.supplygo.protocol.Response;
import com.supplygo.service.AuthException;
import com.supplygo.service.AuthService;
import com.supplygo.service.UserSession;

/** Per-connection dispatch. Clients cannot supply a trusted identity or session role. */
final class RequestRouter {
    private final AuthService authentication;
    private final boolean secure;
    private final com.supplygo.service.InventoryService inventory;
    private com.supplygo.service.BusinessProfileService businessProfiles;
    private final UserSession session = new UserSession();

    RequestRouter(AuthService authentication, boolean secure, com.supplygo.service.InventoryService inventory) {
        this.authentication = authentication;
        this.secure = secure;
        this.inventory = inventory;
    }

    RequestRouter(AuthService authentication, boolean secure, InventoryService inventory,
            com.supplygo.service.BusinessProfileService profiles) {
        this(authentication, secure, inventory); this.businessProfiles = profiles;
    }

    Response handle(Request request) {
        String id = request.requestId();
        if (request.type() == com.supplygo.protocol.MessageType.PING) return Response.ok(id, "PONG");
        if (request.type() == com.supplygo.protocol.MessageType.QUIT) { session.logout(); return Response.ok(id, "BYE"); }
        if (!secure) return Response.error(id, "TLS_REQUIRED", "Use an encrypted TLS connection for protected operations");
        if (authentication == null) return Response.error(id, "AUTH_UNAVAILABLE", "Authentication is not configured");
        try {
            return switch (request.type()) {
                case LOGIN, REGISTER -> {
                    JsonObject data = request.payload();
                    char[] password = data.get("password").getAsString().toCharArray();
                    try {
                        if (request.type() == com.supplygo.protocol.MessageType.LOGIN) {
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
                    if (request.type() == com.supplygo.protocol.MessageType.LIST_PRODUCTS || request.type() == com.supplygo.protocol.MessageType.LIST_BATCHES) {
                        boolean batches = request.type() == com.supplygo.protocol.MessageType.LIST_BATCHES;
                        yield Response.ok(id, batches ? "BATCHES" : "PRODUCTS", inventory.list(actor, batches, data.get("offset").getAsInt()));
                    }
                    yield Response.ok(id, "INVENTORY_UPDATED");
                }
                case GET_BUSINESS_PROFILE, UPDATE_BUSINESS_PROFILE, LIST_SUPPLIERS, LIST_SELLERS -> {
                    UserProfile actor = session.requireUser();
                    if (businessProfiles == null) throw new AuthException("PROFILES_UNAVAILABLE", "Business profiles are not configured");
                    JsonObject data = request.payload(), attributes = new JsonObject();
                    if (request.type() == MessageType.LIST_SUPPLIERS || request.type() == MessageType.LIST_SELLERS) {
                        var page = businessProfiles.list(actor, request.type() == MessageType.LIST_SUPPLIERS ? Role.SUPPLIER : Role.SELLER,
                                data.get("offset").getAsInt());
                        JsonArray rows = new JsonArray(); page.profiles().forEach(p -> rows.add(com.supplygo.protocol.BusinessProfileData.encode(p)));
                        attributes.add("profiles", rows); attributes.addProperty("nextOffset", page.nextOffset());
                        yield Response.ok(id, "BUSINESS_PROFILES", attributes);
                    }
                    var result = request.type() == MessageType.GET_BUSINESS_PROFILE ? businessProfiles.get(actor)
                            : businessProfiles.update(actor, data.get("fullName").getAsString(), data.get("businessName").getAsString(),
                                    data.get("contactNumber").getAsString(), data.get("location").getAsString());
                    attributes.add("profile", com.supplygo.protocol.BusinessProfileData.encode(result));
                    yield Response.ok(id, "BUSINESS_PROFILE", attributes);
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
