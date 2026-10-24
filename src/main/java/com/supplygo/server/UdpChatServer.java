package com.supplygo.server;

import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import com.google.gson.JsonObject;
import com.supplygo.model.UserProfile;
import com.supplygo.protocol.*;
import com.supplygo.service.AuthException;

/** One bounded shared room. Grants belong to live TCP sessions; invalid packets receive no reply. */
public final class UdpChatServer implements AutoCloseable {
    private static final int MAX_PARTICIPANTS = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final DatagramSocket socket;
    private final Clock clock;
    private final Map<String, Participant> participants = new HashMap<>();
    private Thread receiver;
    private boolean started;
    private volatile boolean closed;
    private static final class Participant {
        final ChatCredentials grant;
        final UserProfile user;
        final InetAddress address;
        SocketAddress endpoint;
        Instant lastSeen;
        long incoming, outgoing;
        long rateWindow;
        int sends;
        Participant(ChatCredentials grant, UserProfile user, InetAddress address, Instant now) {
            this.grant = grant; this.user = user; this.address = address; this.lastSeen = now;
        }
    }
    public UdpChatServer(String address, int port) throws SocketException { this(address, port, Clock.systemUTC()); }
    public UdpChatServer(String address, int port, Clock clock) throws SocketException {
        this.clock = clock; socket = new DatagramSocket(new InetSocketAddress(address, port)); socket.setSoTimeout(1000);
    }
    public int port() { return socket.getLocalPort(); }
    public synchronized void start() {
        if (started || closed) throw new IllegalStateException("Chat server already started or closed");
        started = true; receiver = Thread.ofPlatform().name("supplygo-udp-chat").start(this::receive);
    }
    public synchronized ChatCredentials issue(UserProfile user, InetAddress address, Instant expires) throws AuthException {
        prune();
        if (closed || participants.size() >= MAX_PARTICIPANTS) throw new AuthException("CHAT_UNAVAILABLE", "Chat is unavailable or full");
        if (user == null || address == null || !clock.instant().isBefore(expires)) throw new AuthException("AUTH_REQUIRED", "Sign in again");
        byte[] token = new byte[16], key = new byte[32]; RANDOM.nextBytes(token); RANDOM.nextBytes(key);
        var grant = new ChatCredentials(HexFormat.of().formatHex(token), Base64.getUrlEncoder().withoutPadding().encodeToString(key), port(), expires.toString());
        participants.put(grant.ticket(), new Participant(grant, user, address, clock.instant())); return grant;
    }
    public synchronized void revoke(String ticket) { if (ticket != null) participants.remove(ticket); }
    public synchronized int participantCount() { prune(); return participants.size(); }
    private void prune() {
        Instant now = clock.instant();
        participants.values().removeIf(p -> !now.isBefore(Instant.parse(p.grant.expiresAt())) || !now.isBefore(p.lastSeen.plusSeconds(45)));
    }
    private void receive() {
        byte[] buffer = new byte[UdpChatProtocol.MAX_PACKET + 1];
        while (!closed) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try { socket.receive(packet); handle(packet); }
            catch (SocketTimeoutException ignored) { synchronized (this) { prune(); } }
            catch (IOException ignored) { if (socket.isClosed()) break; }
        }
    }
    private synchronized void handle(DatagramPacket datagram) {
        prune();
        try {
            var packet = UdpChatProtocol.parse(datagram.getData(), datagram.getLength());
            Participant sender = participants.get(packet.ticket());
            if (sender == null || !sender.address.equals(datagram.getAddress()) || packet.sequence() <= sender.incoming
                    || sender.endpoint != null && !sender.endpoint.equals(datagram.getSocketAddress())) return;
            JsonObject body = UdpChatProtocol.verify(packet, sender.grant, false);
            String type = UdpChatProtocol.string(body, "type");
            if (!body.keySet().equals(type.equals("SEND") ? Set.of("type", "text") : Set.of("type"))) return;
            if (!Set.of("JOIN", "SEND", "HEARTBEAT", "LEAVE").contains(type)) return;
            if (sender.endpoint == null && !type.equals("JOIN")) return;
            sender.incoming = packet.sequence(); sender.lastSeen = clock.instant();
            if (type.equals("JOIN")) { sender.endpoint = datagram.getSocketAddress(); ack(sender, packet.sequence(), "OK", "Joined shared room"); return; }
            if (type.equals("LEAVE")) { ack(sender, packet.sequence(), "OK", "Left chat"); participants.remove(packet.ticket()); return; }
            if (type.equals("HEARTBEAT")) { ack(sender, packet.sequence(), "OK", "Alive"); return; }
            String text;
            try { text = UdpChatProtocol.text(UdpChatProtocol.string(body, "text")); }
            catch (IllegalArgumentException invalid) { ack(sender, packet.sequence(), "ERROR", invalid.getMessage()); return; }
            long now = clock.millis() / 1000;
            if (sender.rateWindow != now) { sender.rateWindow = now; sender.sends = 0; }
            if (++sender.sends > 5) { ack(sender, packet.sequence(), "ERROR", "Send at most five messages per second"); return; }
            JsonObject event = UdpChatProtocol.command("MESSAGE");
            event.addProperty("sender", sender.user.username()); event.addProperty("role", sender.user.role().name());
            event.addProperty("text", text); event.addProperty("time", clock.instant().toString());
            ack(sender, packet.sequence(), "OK", "Accepted by server; recipient delivery is not guaranteed");
            for (Participant recipient : participants.values()) if (recipient.endpoint != null) send(recipient, event);
        } catch (IOException | IllegalArgumentException ignored) { /* Malformed, forged or oversized datagrams are silently dropped. */ }
    }
    private void ack(Participant user, long request, String status, String message) {
        JsonObject response = UdpChatProtocol.command("ACK"); response.addProperty("replyTo", request);
        response.addProperty("status", status); response.addProperty("message", message); send(user, response);
    }
    private void send(Participant user, JsonObject body) {
        if (user.outgoing == Long.MAX_VALUE) return;
        byte[] packet = UdpChatProtocol.encode(user.grant, ++user.outgoing, body, true);
        try { socket.send(new DatagramPacket(packet, packet.length, user.endpoint)); }
        catch (IOException ignored) { /* UDP delivery is best effort; other participants still receive their copies. */ }
    }
    @Override public void close() {
        Thread worker;
        synchronized (this) { closed = true; participants.clear(); socket.close(); worker = receiver; }
        if (worker != null && worker != Thread.currentThread()) {
            try { worker.join(2000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
    }
}
