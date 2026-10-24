package com.supplygo.client;

import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import com.google.gson.JsonObject;
import com.supplygo.model.Role;
import com.supplygo.protocol.*;

/** Connected UDP socket, bounded receive loop, request acknowledgements and replay rejection. */
public final class UdpChatClient implements AutoCloseable {
    public record Message(String sender, Role role, String text, String time) { }
    private final ChatCredentials grant;
    private final DatagramSocket socket;
    private final Consumer<Message> onMessage;
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final Thread receiver;
    private volatile boolean closed;
    private long incoming;
    public UdpChatClient(InetAddress address, ChatCredentials grant, Consumer<Message> onMessage) throws IOException {
        this.grant = grant; this.onMessage = onMessage;
        socket = new DatagramSocket();
        try { socket.connect(address, grant.port()); socket.setSoTimeout(1000); }
        catch (RuntimeException | SocketException exception) { socket.close(); throw exception; }
        receiver = Thread.ofPlatform().daemon().name("supplygo-udp-receiver").start(this::receive);
    }
    public synchronized void join() throws IOException { request(UdpChatProtocol.command("JOIN")); }
    public synchronized String send(String text) throws IOException {
        JsonObject payload = UdpChatProtocol.command("SEND"); payload.addProperty("text", UdpChatProtocol.text(text));
        return request(payload);
    }
    public synchronized void heartbeat() throws IOException { request(UdpChatProtocol.command("HEARTBEAT")); }
    public synchronized void leave() throws IOException { try { request(UdpChatProtocol.command("LEAVE")); } finally { close(); } }
    private String request(JsonObject payload) throws IOException {
        if (closed) throw new IOException("Chat is closed; join again");
        if (!Instant.now().isBefore(Instant.parse(grant.expiresAt()))) { close(); throw new IOException("Chat session expired; sign in again"); }
        long id = sequence.incrementAndGet();
        if (id <= 0) { close(); throw new IOException("Chat sequence exhausted"); }
        CompletableFuture<JsonObject> result = new CompletableFuture<>(); pending.put(id, result);
        try {
            byte[] bytes = UdpChatProtocol.encode(grant, id, payload, false); socket.send(new DatagramPacket(bytes, bytes.length));
            JsonObject response = result.get(3, TimeUnit.SECONDS);
            String message = UdpChatProtocol.string(response, "message");
            if (!UdpChatProtocol.string(response, "status").equals("OK")) throw new IOException(message);
            return message;
        } catch (TimeoutException exception) { throw new IOException("No UDP confirmation. Delivery is unknown; check the room before resending."); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException("Chat interrupted", exception); }
        catch (ExecutionException exception) { throw new IOException("Chat closed", exception); }
        finally { pending.remove(id); }
    }
    private void receive() {
        byte[] buffer = new byte[UdpChatProtocol.MAX_PACKET + 1];
        while (!closed) {
            try {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length); socket.receive(datagram);
                var packet = UdpChatProtocol.parse(datagram.getData(), datagram.getLength());
                if (packet.sequence() <= incoming) continue;
                JsonObject body = UdpChatProtocol.verify(packet, grant, true);
                String type = UdpChatProtocol.string(body, "type");
                if (type.equals("ACK") && body.keySet().equals(Set.of("type", "replyTo", "status", "message"))) {
                    long replyTo = UdpChatProtocol.integer(body, "replyTo");
                    if (!Set.of("OK", "ERROR").contains(UdpChatProtocol.string(body, "status"))
                            || UdpChatProtocol.string(body, "message").length() > 200) continue;
                    incoming = packet.sequence(); var waiting = pending.get(replyTo); if (waiting != null) waiting.complete(body);
                } else if (type.equals("MESSAGE") && body.keySet().equals(Set.of("type", "sender", "role", "text", "time"))) {
                    String sender = UdpChatProtocol.string(body, "sender"), time = UdpChatProtocol.string(body, "time");
                    if (!sender.matches("[a-z][a-z0-9_]{2,31}")) continue;
                    Instant.parse(time);
                    Message message = new Message(sender, Role.valueOf(UdpChatProtocol.string(body, "role")),
                            UdpChatProtocol.text(UdpChatProtocol.string(body, "text")), time);
                    incoming = packet.sequence(); onMessage.accept(message);
                }
            } catch (SocketTimeoutException ignored) { /* Allows orderly expiry and close. */ }
            catch (IOException | IllegalArgumentException ignored) { if (socket.isClosed()) break; }
        }
    }
    @Override public void close() {
        closed = true; socket.close(); pending.values().forEach(p -> p.completeExceptionally(new IOException("Chat closed")));
        pending.clear();
    }
}
