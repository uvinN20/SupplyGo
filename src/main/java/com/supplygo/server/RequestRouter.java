package com.supplygo.server;

import java.util.Arrays;
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
    private final UserSession session = new UserSession();

    RequestRouter(AuthService authentication, boolean secure) {
        this.authentication = authentication;
        this.secure = secure;
    }

    Response handle(Request request) {
        String id = request.requestId();
        if (request.type() == com.supplygo.protocol.MessageType.PING) return Response.ok(id, "PONG");
        if (request.type() == com.supplygo.protocol.MessageType.QUIT) { session.logout(); return Response.ok(id, "BYE"); }
        if (!secure) return Response.error(id, "TLS_REQUIRED", "Use an encrypted TLS connection for account operations");
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
