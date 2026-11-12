package com.supplygo.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.supplygo.client.ServerRejectedException;
import com.supplygo.client.SupplyGoTcpClient;
import com.supplygo.model.BusinessProfile;
import com.supplygo.model.OrderStatus;
import com.supplygo.model.SupplyOrder;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import com.supplygo.protocol.BusinessProfileData;
import com.supplygo.protocol.OrderData;
import com.supplygo.protocol.ProtocolCodec;
import com.supplygo.protocol.ProtocolException;
import com.supplygo.security.TlsSupport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLContext;

/** Same-origin HTTP session bridge from the browser to the authenticated TLS/TCP service. */
public final class WebGatewayServer implements AutoCloseable {
    public static final String SESSION_COOKIE = "SUPPLYGO_SESSION";
    public static final String CSRF_HEADER = "X-SupplyGo-Request";
    public static final int MAX_BODY_BYTES = 8192;
    public static final int SESSION_IDLE_SECONDS = 900;
    public static final int SESSION_ABSOLUTE_SECONDS = 28800;
    private static final int MAX_SESSIONS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> SESSION_FIELDS = Set.of("username", "password");
    private static final Set<String> REGISTER_FIELDS = Set.of("username", "password", "role");
    private final String tcpHost;
    private final int tcpPort;
    private final SSLContext tls;
    private final HttpServer server;
    private final ExecutorService workers;
    private final ScheduledExecutorService heartbeat;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final boolean secureCookies;
    private volatile boolean closed;

    public WebGatewayServer(String bindAddress, int httpPort, String tcpHost, int tcpPort,
            SSLContext tls, boolean secureCookies) throws IOException {
        if (bindAddress == null || bindAddress.isBlank() || tcpHost == null || tcpHost.isBlank())
            throw new IllegalArgumentException("Web and TCP bind addresses must not be blank");
        if (httpPort < 0 || httpPort > 65535 || tcpPort < 1 || tcpPort > 65535)
            throw new IllegalArgumentException("Invalid web or TCP port");
        if (tls == null) throw new IllegalArgumentException("Browser authentication requires verified TLS to the TCP server");
        this.tcpHost = tcpHost.strip(); this.tcpPort = tcpPort; this.tls = tls; this.secureCookies = secureCookies;
        this.server = HttpServer.create(new InetSocketAddress(bindAddress.strip(), httpPort), 32);
        ThreadFactory factory = Thread.ofPlatform().name("supplygo-web-", 0).factory();
        this.workers = Executors.newFixedThreadPool(8, factory);
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("supplygo-web-session-", 0).factory());
        this.heartbeat.scheduleAtFixedRate(this::keepSessionsAlive, 25, 25, TimeUnit.SECONDS);
        server.setExecutor(workers);
        server.createContext("/api/", this::handle);
    }

    public int port() { return server.getAddress().getPort(); }
    public void start() { if (closed) throw new IllegalStateException("Web gateway is closed"); server.start(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            securityHeaders(exchange);
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) { send(exchange, 204, new JsonObject()); return; }
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                pruneExpired();
                if (path.equals("/api/health") && method.equals("GET")) { send(exchange, 200, json("status", "ok")); return; }
                if (path.equals("/api/session") && method.equals("POST")) { requireBrowserRequest(exchange); login(exchange); return; }
                if (path.equals("/api/accounts") && method.equals("POST")) { requireBrowserRequest(exchange); register(exchange); return; }
                if (path.equals("/api/session") && method.equals("GET")) { getSession(exchange); return; }
                if (path.equals("/api/session") && method.equals("DELETE")) { requireBrowserRequest(exchange); logout(exchange); return; }
                if (path.equals("/api/inventory") && method.equals("GET")) { listInventory(exchange); return; }
                if (path.equals("/api/inventory/products") && method.equals("POST")) { requireBrowserRequest(exchange); createProduct(exchange); return; }
                if (path.equals("/api/inventory/stock") && method.equals("POST")) { requireBrowserRequest(exchange); changeStock(exchange); return; }
                if (path.equals("/api/orders") && method.equals("GET")) { listOrders(exchange); return; }
                if (path.equals("/api/orders") && method.equals("POST")) { requireBrowserRequest(exchange); createOrder(exchange); return; }
                if (path.equals("/api/orders/status") && method.equals("POST")) { requireBrowserRequest(exchange); changeOrderStatus(exchange); return; }
                if (path.equals("/api/business-profile") && method.equals("GET")) { getBusinessProfile(exchange); return; }
                if (path.equals("/api/business-profile") && method.equals("PUT")) { requireBrowserRequest(exchange); saveBusinessProfile(exchange); return; }
                if (path.equals("/api/business-profiles/suppliers") && method.equals("GET")) { listBusinessProfiles(exchange, true); return; }
                if (path.equals("/api/business-profiles/sellers") && method.equals("GET")) { listBusinessProfiles(exchange, false); return; }
                sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            } catch (ApiFailure failure) {
                sendError(exchange, failure.status, failure.code, failure.getMessage());
            } catch (Exception exception) {
                sendError(exchange, 500, "INTERNAL_ERROR", "The request could not be completed");
                System.err.println("SupplyGo web request failed: " + exception.getClass().getSimpleName());
            }
        }
    }

    private void login(HttpExchange exchange) throws IOException, ApiFailure {
        JsonObject body = requestBody(exchange, SESSION_FIELDS);
        String username = requiredText(body, "username", 3, 32);
        char[] password = requiredText(body, "password", 12, 128).toCharArray();
        SupplyGoTcpClient client = newClient();
        try {
            client.connect();
            UserProfile profile = client.login(username, password);
            if (sessions.size() >= MAX_SESSIONS) { client.close(); throw new ApiFailure(503, "SERVER_BUSY", "The web session limit has been reached"); }
            String id = token();
            sessions.put(id, new Session(id, profile, client, System.currentTimeMillis()));
            setCookie(exchange, id, SESSION_IDLE_SECONDS);
            send(exchange, 200, profileJson(profile));
        } catch (ServerRejectedException rejected) {
            client.close(); throw new ApiFailure(401, rejected.code(), rejected.getMessage());
        } catch (IOException failure) {
            client.close(); throw new ApiFailure(502, "AUTH_SERVICE_UNAVAILABLE", "Could not reach the secure SupplyGo authentication service");
        } finally { Arrays.fill(password, '\0'); }
    }

    private void register(HttpExchange exchange) throws IOException, ApiFailure {
        JsonObject body = requestBody(exchange, REGISTER_FIELDS);
        String username = requiredText(body, "username", 3, 32);
        char[] password = requiredText(body, "password", 12, 128).toCharArray();
        String roleText = requiredText(body, "role", 1, 32);
        Role role;
        try { role = Role.valueOf(roleText); }
        catch (IllegalArgumentException error) { throw new ApiFailure(400, "INVALID_ROLE", "Choose a supported supplier or seller account role"); }
        if (role != Role.SUPPLIER && role != Role.SELLER) throw new ApiFailure(400, "INVALID_ROLE", "Only supplier and seller accounts can self-register");
        SupplyGoTcpClient client = newClient();
        try {
            client.connect();
            UserProfile created = client.register(username, password, role);
            client.disconnect();
            send(exchange, 201, profileJson(created));
        } catch (ServerRejectedException rejected) {
            client.close(); throw new ApiFailure(400, rejected.code(), rejected.getMessage());
        } catch (IOException failure) {
            client.close(); throw new ApiFailure(502, "AUTH_SERVICE_UNAVAILABLE", "Could not reach the secure SupplyGo authentication service");
        } finally { Arrays.fill(password, '\0'); }
    }

    private void listInventory(HttpExchange exchange) throws IOException, ApiFailure {
        Session session = requireSession(exchange);
        int offset = 0;
        JsonArray products = new JsonArray();
        try {
            do {
                JsonObject page = session.client.inventoryPage(false, offset);
                JsonArray rows = page.getAsJsonArray("products");
                if (rows == null) throw new IOException("Invalid inventory response");
                rows.forEach(row -> products.add(row.deepCopy()));
                int next = page.get("nextOffset").getAsInt();
                if (next < 0) break;
                if (next <= offset || next > 10000) throw new IOException("Invalid inventory pagination");
                offset = next;
            } while (products.size() <= 1000);
            JsonObject response = new JsonObject();
            response.add("products", products);
            send(exchange, 200, response);
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "INVENTORY_SERVICE_UNAVAILABLE", "Could not load inventory from the SupplyGo server");
        }
    }

    private void createProduct(HttpExchange exchange) throws IOException, ApiFailure {
        JsonObject body = requestBody(exchange, Set.of("id", "name", "quantity"));
        String id = requiredText(body, "id", 1, 24).toUpperCase(java.util.Locale.ROOT);
        String name = requiredText(body, "name", 1, 60);
        int quantity = requiredQuantity(body);
        Session session = requireSession(exchange);
        try {
            session.client.createProduct(id, name);
            if (quantity > 0) session.client.changeStock(true, id, quantity);
            send(exchange, 201, json("status", "created"));
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "INVENTORY_SERVICE_UNAVAILABLE", "Could not save the product to the SupplyGo server");
        }
    }

    private void changeStock(HttpExchange exchange) throws IOException, ApiFailure {
        JsonObject body = requestBody(exchange, Set.of("productId", "quantity", "action"));
        String productId = requiredText(body, "productId", 1, 24).toUpperCase(java.util.Locale.ROOT);
        String action = requiredText(body, "action", 1, 16);
        if (!action.equals("receive") && !action.equals("withdraw"))
            throw new ApiFailure(400, "INVALID_REQUEST", "Stock action must be receive or withdraw");
        int quantity = requiredQuantity(body);
        Session session = requireSession(exchange);
        try {
            session.client.changeStock(action.equals("receive"), productId, quantity);
            send(exchange, 200, json("status", "updated"));
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "INVENTORY_SERVICE_UNAVAILABLE", "Could not update stock on the SupplyGo server");
        }
    }

    private void listOrders(HttpExchange exchange) throws IOException, ApiFailure {
        Session session = requireSession(exchange);
        int offset = 0;
        JsonArray orders = new JsonArray();
        try {
            do {
                JsonObject page = session.client.orderPage(offset);
                JsonArray rows = page.getAsJsonArray("orders");
                if (rows == null || !page.has("nextOffset")) throw new IOException("Invalid orders response");
                rows.forEach(row -> orders.add(row.deepCopy()));
                int next = page.get("nextOffset").getAsInt();
                if (next < 0) break;
                if (next <= offset || next > 10000) throw new IOException("Invalid order pagination");
                offset = next;
            } while (orders.size() <= 10000);
            JsonObject response = new JsonObject();
            response.add("orders", orders);
            send(exchange, 200, response);
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "ORDER_SERVICE_UNAVAILABLE", "Could not load orders from the SupplyGo server");
        }
    }

    private void createOrder(HttpExchange exchange) throws IOException, ApiFailure {
        JsonObject body = requestBody(exchange, Set.of("clientOrderId", "productId", "quantity"));
        String clientOrderId = requiredText(body, "clientOrderId", 36, 36);
        String productId = requiredText(body, "productId", 1, 24).toUpperCase(java.util.Locale.ROOT);
        int quantity = requiredQuantity(body);
        if (quantity == 0) throw new ApiFailure(400, "INVALID_ORDER", "Order quantity must be at least 1");
        Session session = requireSession(exchange);
        try {
            SupplyOrder order = session.client.createOrder(clientOrderId, productId, quantity);
            JsonObject response = new JsonObject();
            response.add("order", OrderData.encode(order));
            send(exchange, 201, response);
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "ORDER_SERVICE_UNAVAILABLE", "Could not submit the order to the SupplyGo server");
        }
    }

    private void changeOrderStatus(HttpExchange exchange) throws IOException, ApiFailure {
        JsonObject body = requestBody(exchange, Set.of("orderId", "status"));
        long orderId = requiredPositiveLong(body, "orderId");
        String requested = requiredText(body, "status", 1, 16);
        OrderStatus target;
        try { target = OrderStatus.valueOf(requested); }
        catch (IllegalArgumentException error) { throw new ApiFailure(400, "INVALID_ORDER", "Choose a supported order status"); }
        if (target != OrderStatus.APPROVED && target != OrderStatus.FULFILLED && target != OrderStatus.CANCELLED)
            throw new ApiFailure(400, "INVALID_ORDER", "Choose a supported order action");
        Session session = requireSession(exchange);
        try {
            SupplyOrder order = session.client.changeOrder(orderId, target);
            JsonObject response = new JsonObject();
            response.add("order", OrderData.encode(order));
            send(exchange, 200, response);
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "ORDER_SERVICE_UNAVAILABLE", "Could not update the order on the SupplyGo server");
        }
    }

    private void getBusinessProfile(HttpExchange exchange) throws IOException, ApiFailure {
        Session session = requireSession(exchange);
        try {
            JsonObject response = new JsonObject();
            response.add("profile", BusinessProfileData.encode(session.client.businessProfile()));
            send(exchange, 200, response);
        } catch (ServerRejectedException rejected) {
            if ("PROFILE_NOT_FOUND".equals(rejected.code())) {
                JsonObject response = new JsonObject(); response.add("profile", com.google.gson.JsonNull.INSTANCE);
                send(exchange, 200, response); return;
            }
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "PROFILE_SERVICE_UNAVAILABLE", "Could not load the business profile from the SupplyGo server");
        }
    }

    private void saveBusinessProfile(HttpExchange exchange) throws IOException, ApiFailure {
        JsonObject body = requestBody(exchange, Set.of("fullName", "businessName", "contactNumber", "location"));
        String fullName = requiredText(body, "fullName", 1, 64);
        String businessName = requiredText(body, "businessName", 1, 96);
        String contactNumber = requiredText(body, "contactNumber", 1, 32);
        String location = requiredText(body, "location", 1, 64);
        Session session = requireSession(exchange);
        try {
            JsonObject response = new JsonObject();
            response.add("profile", BusinessProfileData.encode(
                    session.client.updateBusinessProfile(fullName, businessName, contactNumber, location)));
            send(exchange, 200, response);
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "PROFILE_SERVICE_UNAVAILABLE", "Could not save the business profile to the SupplyGo server");
        }
    }

    private void listBusinessProfiles(HttpExchange exchange, boolean suppliers) throws IOException, ApiFailure {
        Session session = requireSession(exchange);
        int offset = queryOffset(exchange);
        try {
            JsonObject page = session.client.businessDirectory(suppliers, offset);
            JsonArray profiles = page.getAsJsonArray("profiles");
            if (profiles == null || !page.has("nextOffset")) throw new IOException("Invalid business profile response");
            JsonObject response = new JsonObject();
            response.add("profiles", profiles.deepCopy());
            response.add("nextOffset", page.get("nextOffset").deepCopy());
            send(exchange, 200, response);
        } catch (ServerRejectedException rejected) {
            throw rejectedFailure(rejected);
        } catch (IOException failure) {
            throw new ApiFailure(502, "PROFILE_SERVICE_UNAVAILABLE", "Could not load the business directory from the SupplyGo server");
        }
    }

    private static int queryOffset(HttpExchange exchange) throws ApiFailure {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) return 0;
        if (!query.matches("offset=(0|[1-9][0-9]{0,4})"))
            throw new ApiFailure(400, "INVALID_REQUEST", "Directory offset is invalid");
        try {
            int offset = Integer.parseInt(query.substring("offset=".length()));
            if (offset > 10000) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException error) {
            throw new ApiFailure(400, "INVALID_REQUEST", "Directory offset must be between 0 and 10000");
        }
    }

    private static long requiredPositiveLong(JsonObject body, String field) throws ApiFailure {
        if (!(body.get(field) instanceof JsonPrimitive value) || !value.isNumber()
                || !value.getAsString().matches("[1-9][0-9]{0,18}"))
            throw new ApiFailure(400, "INVALID_REQUEST", field + " must be a positive whole number");
        try { return Long.parseLong(value.getAsString()); }
        catch (NumberFormatException error) { throw new ApiFailure(400, "INVALID_REQUEST", field + " is out of range"); }
    }

    private static int requiredQuantity(JsonObject body) throws ApiFailure {
        if (!(body.get("quantity") instanceof JsonPrimitive value) || !value.isNumber())
            throw new ApiFailure(400, "INVALID_REQUEST", "quantity must be a whole number");
        String text = value.getAsString();
        if (!text.matches("0|[1-9][0-9]{0,6}"))
            throw new ApiFailure(400, "INVALID_REQUEST", "quantity must be a whole number from 0 to 1000000");
        int quantity = Integer.parseInt(text);
        if (quantity > 1_000_000)
            throw new ApiFailure(400, "INVALID_REQUEST", "quantity must be a whole number from 0 to 1000000");
        return quantity;
    }

    private Session requireSession(HttpExchange exchange) throws ApiFailure {
        Session session = findSession(exchange);
        if (session == null) { clearCookie(exchange); throw new ApiFailure(401, "UNAUTHENTICATED", "Sign in to continue"); }
        return session;
    }

    private static ApiFailure rejectedFailure(ServerRejectedException rejected) {
        int status = switch (rejected.code()) {
            case "AUTH_REQUIRED" -> 401;
            case "FORBIDDEN" -> 403;
            default -> 400;
        };
        return new ApiFailure(status, rejected.code(), rejected.getMessage());
    }

    private void getSession(HttpExchange exchange) throws IOException, ApiFailure {
        Session session = requireSession(exchange);
        session.touch(); setCookie(exchange, session.id, remainingIdle(session)); send(exchange, 200, profileJson(session.profile));
    }

    private void logout(HttpExchange exchange) throws IOException {
        String id = cookie(exchange, SESSION_COOKIE);
        if (id != null) { Session session = sessions.remove(id); if (session != null) session.client.close(); }
        clearCookie(exchange); send(exchange, 200, json("status", "signed_out"));
    }

    private Session findSession(HttpExchange exchange) throws ApiFailure {
        String id = cookie(exchange, SESSION_COOKIE);
        if (id == null) return null;
        Session session = sessions.get(id);
        if (session == null) return null;
        long now = System.currentTimeMillis();
        if (now - session.createdAt > SESSION_ABSOLUTE_SECONDS * 1000L || now - session.lastAccess > SESSION_IDLE_SECONDS * 1000L) {
            if (sessions.remove(id, session)) session.client.close();
            return null;
        }
        session.touch(); return session;
    }

    private void keepSessionsAlive() {
        if (closed) return;
        pruneExpired();
        sessions.forEach((id, session) -> {
            try { session.client.ping(); }
            catch (IOException failure) { if (sessions.remove(id, session)) session.client.close(); }
        });
    }
    private void pruneExpired() {
        long now = System.currentTimeMillis();
        sessions.forEach((id, session) -> {
            if (now - session.createdAt > SESSION_ABSOLUTE_SECONDS * 1000L || now - session.lastAccess > SESSION_IDLE_SECONDS * 1000L)
                if (sessions.remove(id, session)) session.client.close();
        });
    }
    private static int remainingIdle(Session session) {
        return (int) Math.max(1, SESSION_IDLE_SECONDS - (System.currentTimeMillis() - session.lastAccess) / 1000);
    }
    private SupplyGoTcpClient newClient() { return new SupplyGoTcpClient(tcpHost, tcpPort, tls); }

    private JsonObject requestBody(HttpExchange exchange, Set<String> fields) throws IOException, ApiFailure {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
            throw new ApiFailure(415, "UNSUPPORTED_MEDIA_TYPE", "Send a JSON request");
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new ApiFailure(413, "REQUEST_TOO_LARGE", "Request body is too large");
        try {
            JsonObject body = ProtocolCodec.parse(new String(bytes, StandardCharsets.UTF_8));
            if (!body.keySet().equals(fields)) throw new ApiFailure(400, "INVALID_REQUEST", "Request fields are invalid");
            return body;
        } catch (ProtocolException | RuntimeException error) {
            throw new ApiFailure(400, "INVALID_JSON", "Request must contain one valid JSON object with unique keys");
        }
    }
    private static String requiredText(JsonObject object, String key, int min, int max) throws ApiFailure {
        if (!(object.get(key) instanceof JsonPrimitive value) || !value.isString()) throw new ApiFailure(400, "INVALID_REQUEST", key + " must be text");
        String result = value.getAsString();
        if (result.length() < min || result.length() > max) throw new ApiFailure(400, "INVALID_REQUEST", key + " length is invalid");
        return result;
    }
    private static void requireBrowserRequest(HttpExchange exchange) throws ApiFailure {
        if (!"1".equals(exchange.getRequestHeaders().getFirst(CSRF_HEADER))) throw new ApiFailure(403, "CSRF_REJECTED", "Request origin could not be verified");
    }
    private static String cookie(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String piece : header.split(";")) {
            String[] pair = piece.strip().split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                String value = pair[1];
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
                    value = value.substring(1, value.length() - 1);
                return value;
            }
        }
        return null;
    }
    private void setCookie(HttpExchange exchange, String id, int maxAge) {
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + id + "; Path=/api; Max-Age=" + maxAge + "; HttpOnly; SameSite=Strict" + (secureCookies ? "; Secure" : ""));
    }
    private void clearCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/api; Max-Age=0; HttpOnly; SameSite=Strict" + (secureCookies ? "; Secure" : ""));
    }
    private static String token() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static JsonObject profileJson(UserProfile profile) {
        JsonObject result = new JsonObject(); result.addProperty("username", profile.username()); result.addProperty("role", profile.role().name()); return result;
    }
    private static JsonObject json(String key, String value) { JsonObject result = new JsonObject(); result.addProperty(key, value); return result; }
    private static void securityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store"); headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer"); headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    }
    private static void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        JsonObject error = new JsonObject(); error.addProperty("code", code); error.addProperty("message", message);
        JsonObject response = new JsonObject(); response.add("error", error); send(exchange, status, response);
    }
    private static void safeError(HttpExchange exchange, int status, String code, String message) {
        try { sendError(exchange, status, code, message); } catch (IOException ignored) { }
    }
    private static void send(HttpExchange exchange, int status, JsonObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
    }
    @Override public void close() {
        if (closed) return; closed = true; server.stop(1); workers.shutdownNow();
        heartbeat.shutdownNow(); sessions.values().forEach(session -> session.client.close()); sessions.clear();
    }
    private static final class Session {
        final String id; final UserProfile profile; final SupplyGoTcpClient client; final long createdAt = System.currentTimeMillis();
        volatile long lastAccess;
        Session(String id, UserProfile profile, SupplyGoTcpClient client, long now) { this.id=id;this.profile=profile;this.client=client;this.lastAccess=now; }
        void touch() { lastAccess=System.currentTimeMillis(); }
    }
    private static final class ApiFailure extends Exception {
        final int status; final String code;
        ApiFailure(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    }
}
