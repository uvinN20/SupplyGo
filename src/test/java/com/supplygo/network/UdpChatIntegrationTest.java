package com.supplygo.network;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import com.google.gson.JsonObject;
import com.supplygo.client.UdpChatClient;
import com.supplygo.model.*;
import com.supplygo.protocol.*;
import com.supplygo.server.UdpChatServer;
import com.supplygo.service.AuthException;

@Timeout(20)
class UdpChatIntegrationTest {
    static final InetAddress LOOP = InetAddress.getLoopbackAddress();
    static class MutableClock extends Clock {
        Instant now = Instant.now();
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
    ChatCredentials grant(UdpChatServer server, String user) throws Exception {
        return server.issue(new UserProfile(user, Role.SELLER), LOOP, Instant.now().plusSeconds(900));
    }
    DatagramSocket raw(UdpChatServer server) throws Exception {
        var socket = new DatagramSocket(); socket.connect(LOOP, server.port()); socket.setSoTimeout(200); return socket;
    }
    void send(DatagramSocket socket, byte[] bytes) throws Exception { socket.send(new DatagramPacket(bytes, bytes.length)); }
    JsonObject receive(DatagramSocket socket, ChatCredentials grant) throws Exception {
        var packet = new DatagramPacket(new byte[1201], 1201); socket.receive(packet);
        return UdpChatProtocol.verify(UdpChatProtocol.parse(packet.getData(), packet.getLength()), grant, true);
    }
    void silent(DatagramSocket socket) { assertThrows(SocketTimeoutException.class, () -> socket.receive(new DatagramPacket(new byte[1201], 1201))); }

    @Test void twoClientsJoinBroadcastUnicodeHeartbeatLeaveAndReconnect() throws Exception {
        try (var server = new UdpChatServer(LOOP.getHostAddress(), 0)) {
            server.start(); var inboxA = new LinkedBlockingQueue<UdpChatClient.Message>(); var inboxB = new LinkedBlockingQueue<UdpChatClient.Message>();
            var a = grant(server, "alice"); var b = grant(server, "bob");
            try (var alice = new UdpChatClient(LOOP, a, inboxA::add); var bob = new UdpChatClient(LOOP, b, inboxB::add)) {
                alice.join(); bob.join(); alice.heartbeat();
                assertTrue(alice.send("Hello — ආයුබෝවන්").contains("Accepted"));
                var received = inboxB.poll(2, TimeUnit.SECONDS); assertNotNull(received);
                assertEquals("alice", received.sender()); assertEquals("Hello — ආයුබෝවන්", received.text());
                assertEquals(received, inboxA.poll(2, TimeUnit.SECONDS));
                bob.leave(); assertEquals(1, server.participantCount());
                var next = grant(server, "bob");
                try (var reconnected = new UdpChatClient(LOOP, next, inboxB::add)) {
                    reconnected.join(); reconnected.send("Back again"); assertEquals("bob", inboxA.poll(2, TimeUnit.SECONDS).sender());
                }
            }
        }
    }
    @Test void forgeryReplayWrongEndpointAndMalformedPacketsAreIgnored() throws Exception {
        try (var server = new UdpChatServer(LOOP.getHostAddress(), 0)) {
            server.start(); var a = grant(server, "alice");
            try (var socket = raw(server); var other = raw(server)) {
                byte[] join = UdpChatProtocol.encode(a, 1, UdpChatProtocol.command("JOIN"), false);
                send(socket, join); assertEquals("OK", receive(socket, a).get("status").getAsString());
                send(socket, join); silent(socket);
                send(other, UdpChatProtocol.encode(a, 2, UdpChatProtocol.command("JOIN"), false)); silent(other);
                var wrong = new ChatCredentials(a.ticket(), Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]), a.port(), a.expiresAt());
                send(socket, UdpChatProtocol.encode(wrong, 2, UdpChatProtocol.command("HEARTBEAT"), false)); silent(socket);
                send(socket, UdpChatProtocol.encode(a, 2, UdpChatProtocol.command("HEARTBEAT"), true)); silent(socket);
                send(socket, new byte[1300]); silent(socket); send(socket, new byte[]{(byte)0xff}); silent(socket);
                JsonObject forged = UdpChatProtocol.command("SEND"); forged.addProperty("text", "Hello"); forged.addProperty("sender", "admin");
                send(socket, UdpChatProtocol.encode(a, 2, forged, false)); silent(socket);
                send(socket, UdpChatProtocol.encode(a, 2, UdpChatProtocol.command("HEARTBEAT"), false)); assertEquals("OK", receive(socket, a).get("status").getAsString());
                server.revoke(a.ticket()); send(socket, UdpChatProtocol.encode(a, 3, UdpChatProtocol.command("HEARTBEAT"), false)); silent(socket);
            }
        }
    }
    @Test void grantsAreBoundToTcpAddressAndNewJoinIsRequired() throws Exception {
        try (var server = new UdpChatServer(LOOP.getHostAddress(), 0)) {
            server.start(); var a = grant(server, "alice");
            try (var socket = raw(server)) {
                JsonObject text = UdpChatProtocol.command("SEND"); text.addProperty("text", "Before join");
                send(socket, UdpChatProtocol.encode(a, 1, text, false)); silent(socket);
                var wrongAddress = server.issue(new UserProfile("bob", Role.SELLER), InetAddress.getByName("192.0.2.1"), Instant.now().plusSeconds(900));
                send(socket, UdpChatProtocol.encode(wrongAddress, 1, UdpChatProtocol.command("JOIN"), false)); silent(socket);
            }
        }
    }
    @Test void idleExpiryAbsoluteExpiryAndCapacityAreBounded() throws Exception {
        MutableClock clock = new MutableClock();
        try (var server = new UdpChatServer(LOOP.getHostAddress(), 0, clock)) {
            for (int i = 0; i < 32; i++) server.issue(new UserProfile("user" + i, Role.SELLER), LOOP, clock.now.plusSeconds(900));
            assertEquals("CHAT_UNAVAILABLE", assertThrows(AuthException.class, () -> server.issue(new UserProfile("overflow", Role.SELLER), LOOP, clock.now.plusSeconds(900))).code());
            clock.now = clock.now.plusSeconds(45); assertEquals(0, server.participantCount());
            server.issue(new UserProfile("short", Role.SELLER), LOOP, clock.now.plusSeconds(5));
            clock.now = clock.now.plusSeconds(5); assertEquals(0, server.participantCount());
        }
    }
    @Test void serverValidatesTextAndRateLimitsMessages() throws Exception {
        MutableClock clock = new MutableClock();
        try (var server = new UdpChatServer(LOOP.getHostAddress(), 0, clock); var socket = raw(server)) {
            server.start(); var a = grant(server, "alice");
            send(socket, UdpChatProtocol.encode(a, 1, UdpChatProtocol.command("JOIN"), false)); receive(socket, a);
            JsonObject invalid = UdpChatProtocol.command("SEND"); invalid.addProperty("text", " ");
            send(socket, UdpChatProtocol.encode(a, 2, invalid, false)); assertEquals("ERROR", receive(socket, a).get("status").getAsString());
            for (int i = 0; i < 6; i++) {
                JsonObject text = UdpChatProtocol.command("SEND"); text.addProperty("text", "Message " + i);
                send(socket, UdpChatProtocol.encode(a, i + 3, text, false)); var ack = receive(socket, a);
                assertEquals(i < 5 ? "OK" : "ERROR", ack.get("status").getAsString());
                if (i < 5) assertEquals("MESSAGE", receive(socket, a).get("type").getAsString());
            }
        }
    }
    @Test void clientTimesOutAndCloseCancelsBlockedWaitWithoutWaitingForMonitor() throws Exception {
        try (var unused = new DatagramSocket(0, LOOP)) {
            var grant = new ChatCredentials("a".repeat(32), Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]), unused.getLocalPort(), Instant.now().plusSeconds(60).toString());
            try (var client = new UdpChatClient(LOOP, grant, message -> {})) {
                assertTrue(assertThrows(IOException.class, client::join).getMessage().contains("No UDP confirmation"));
            }
            try (var client = new UdpChatClient(LOOP, grant, message -> {}); var executor = Executors.newSingleThreadExecutor()) {
                var waiting = executor.submit(() -> assertThrows(IOException.class, client::join));
                client.close(); assertNotNull(waiting.get(2, TimeUnit.SECONDS));
            }
        }
    }
}
