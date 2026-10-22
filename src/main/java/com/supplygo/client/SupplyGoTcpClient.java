package com.supplygo.client;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import com.google.gson.JsonObject;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import com.supplygo.protocol.*;

/** One connection with sequential exchanges, optional verified TLS, and no automatic retries. */
public final class SupplyGoTcpClient implements AutoCloseable {
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int RESPONSE_TIMEOUT_MS = 5000;
    private final String host;
    private final int port;
    private final SSLContext tls;
    private final AtomicReference<Socket> connection = new AtomicReference<>();
    private volatile boolean closed;
    private BufferedInputStream input;
    private BufferedOutputStream output;

    public SupplyGoTcpClient(String host, int port) { this(host, port, null); }
    public SupplyGoTcpClient(String host, int port, SSLContext tls) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Provide a server address and port from 1 to 65535");
        this.host = host.strip();
        this.port = port;
        this.tls = tls;
    }
    public boolean isSecure() { return tls != null; }

    public synchronized void connect() throws IOException {
        if (closed || connection.get() != null) throw new IOException("Client already connected or closed; create a new client to reconnect");
        Socket socket = tls == null ? new Socket() : tls.getSocketFactory().createSocket();
        connection.set(socket);
        try {
            if (closed) throw new IOException("Client was closed");
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(RESPONSE_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            if (socket instanceof SSLSocket secure) {
                var parameters = secure.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                secure.setSSLParameters(parameters);
                secure.startHandshake();
            }
            input = new BufferedInputStream(socket.getInputStream());
            output = new BufferedOutputStream(socket.getOutputStream());
        } catch (IOException | RuntimeException exception) { close(); throw exception; }
    }

    public synchronized String ping() throws IOException { return message(Request.create(MessageType.PING), "PONG"); }
    public synchronized String disconnect() throws IOException {
        try { return message(Request.create(MessageType.QUIT), "BYE"); }
        finally { close(); }
    }
    public synchronized UserProfile login(String username, char[] password) throws IOException {
        return account(MessageType.LOGIN, username, password, null, "SIGNED_IN");
    }
    public synchronized UserProfile register(String username, char[] password, Role role) throws IOException {
        return account(MessageType.REGISTER, username, password, role, "REGISTERED");
    }
    public synchronized UserProfile profile() throws IOException {
        return profileReply(exchange(Request.create(MessageType.GET_PROFILE)), "PROFILE");
    }
    public synchronized String logout() throws IOException { return message(Request.create(MessageType.LOGOUT), "SIGNED_OUT"); }
    public synchronized int adminUserCount() throws IOException {
        Response reply = exchange(Request.create(MessageType.ADMIN_STATS));
        if (!"ADMIN_STATS".equals(reply.message()) || !reply.attributes().has("userCount")) throw badReply();
        return reply.attributes().get("userCount").getAsInt();
    }

    private UserProfile account(MessageType type, String username, char[] password, Role role, String expected) throws IOException {
        if (!isSecure()) throw new IOException("Account operations require TLS; no credentials were sent");
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", new String(password));
        if (role != null) payload.addProperty("role", role.name());
        return profileReply(exchange(Request.create(type, payload)), expected);
    }
    private UserProfile profileReply(Response reply, String expected) throws IOException {
        JsonObject attributes = reply.attributes();
        if (!expected.equals(reply.message()) || !attributes.has("username") || !attributes.has("role")) throw badReply();
        return new UserProfile(attributes.get("username").getAsString(), Role.valueOf(attributes.get("role").getAsString()));
    }
    private String message(Request request, String expected) throws IOException {
        Response reply = exchange(request);
        if (!expected.equals(reply.message()) || !reply.attributes().isEmpty()) throw badReply();
        return reply.message();
    }
    private ProtocolException badReply() {
        close();
        return new ProtocolException("UNEXPECTED_RESPONSE", null, "Server sent an unexpected response");
    }

    private Response exchange(Request request) throws IOException {
        if (closed || connection.get() == null || input == null) throw new IOException("Client is not connected");
        try {
            FrameIO.write(output, ProtocolCodec.encode(request));
            String frame = FrameIO.read(input);
            if (frame == null) throw new EOFException("Server closed the connection");
            Response reply = ProtocolCodec.decodeResponse(frame);
            if (!request.requestId().equals(reply.requestId())) throw badReply();
            if (reply.status() == Response.Status.ERROR) throw new ServerRejectedException(reply.errorCode(), reply.message());
            return reply;
        } catch (ServerRejectedException exception) { throw exception; }
        catch (IOException exception) { close(); throw exception; }
    }

    /** Avoid the exchange monitor, so closing the UI can cancel a blocked read. */
    @Override public void close() {
        closed = true;
        Socket socket = connection.getAndSet(null);
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { /* Client can no longer be used. */ }
        }
    }
}
