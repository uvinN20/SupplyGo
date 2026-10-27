package com.supplygo.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.supplygo.client.ServerRejectedException;
import com.supplygo.client.SupplyGoTcpClient;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
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

    private void getSession(HttpExchange exchange) throws IOException, ApiFailure {
        Session session = findSession(exchange);
        if (session == null) { clearCookie(exchange); throw new ApiFailure(401, "UNAUTHENTICATED", "Sign in to continue"); }
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
