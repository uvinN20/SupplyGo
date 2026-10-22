package com.supplygo.network;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import com.supplygo.client.SupplyGoTcpClient;
import com.supplygo.protocol.FrameIO;
import com.supplygo.protocol.ProtocolCodec;
import com.supplygo.protocol.ProtocolException;
import com.supplygo.protocol.Response;
import com.supplygo.server.SupplyGoTcpServer;
import com.supplygo.server.ClientHandler;

@Timeout(15)
class TcpIntegrationTest {
    private SupplyGoTcpServer server;
    private Thread serverThread;
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = new SupplyGoTcpServer("127.0.0.1", 0);
        serverThread = Thread.ofPlatform().name("test-accept").start(() -> {
            try {
                server.serve();
            } catch (Throwable exception) {
                serverFailure.set(exception);
            }
        });
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        serverThread.join(3000);
        assertFalse(serverThread.isAlive(), "Accept loop did not stop");
        assertNull(serverFailure.get(), () -> "Server failed: " + serverFailure.get());
    }

    @Test
    void supportsRepeatedPingCleanDisconnectAndReconnect() throws Exception {
        try (var client = client()) {
            client.connect();
            assertEquals("PONG", client.ping());
            assertEquals("PONG", client.ping());
            assertEquals("BYE", client.disconnect());
            assertThrows(IOException.class, client::ping);
        }
        try (var reconnected = client()) {
            reconnected.connect();
            assertEquals("PONG", reconnected.ping());
        }
    }

    @Test
    void processesConcurrentClientsIndependently() throws Exception {
        try (var executor = Executors.newFixedThreadPool(12)) {
            List<Future<String>> replies = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                replies.add(executor.submit(() -> {
                    try (var client = client()) {
                        client.connect();
                        for (int ping = 0; ping < 4; ping++) {
                            assertEquals("PONG", client.ping());
                        }
                        return client.disconnect();
                    }
                }));
            }
            for (var reply : replies) {
                assertEquals("BYE", reply.get(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void keepsConnectionUsableAfterJsonAndRequestErrors() throws Exception {
        try (var socket = socket()) {
            var input = new BufferedInputStream(socket.getInputStream());
            for (String bad : List.of("{", "{\"version\":2,\"requestId\":\"x\",\"type\":\"PING\",\"payload\":{}}",
                    "{\"version\":1,\"requestId\":\"x\",\"type\":\"UNKNOWN\",\"payload\":{}}")) {
                FrameIO.write(socket.getOutputStream(), bad);
                assertEquals(Response.Status.ERROR, ProtocolCodec.decodeResponse(FrameIO.read(input)).status());
            }
            // Two complete requests in one TCP write must produce two correlated replies.
            socket.getOutputStream().write((ping("one") + "\n" + ping("two") + "\n").getBytes(StandardCharsets.UTF_8));
            assertEquals("one", ProtocolCodec.decodeResponse(FrameIO.read(input)).requestId());
            assertEquals("two", ProtocolCodec.decodeResponse(FrameIO.read(input)).requestId());
        }
    }

    @Test
    void acceptsFragmentedRequest() throws Exception {
        try (var socket = socket()) {
            byte[] request = (ping("split") + "\r\n").getBytes(StandardCharsets.UTF_8);
            for (byte value : request) {
                socket.getOutputStream().write(value);
            }
            assertEquals("split", ProtocolCodec.decodeResponse(FrameIO.read(socket.getInputStream())).requestId());
        }
    }

    @Test
    void closesOversizedConnectionWhileOtherClientsStillWork() throws Exception {
        try (var socket = socket()) {
            socket.getOutputStream().write(("x".repeat(FrameIO.MAX_FRAME_BYTES + 1) + "\n").getBytes(StandardCharsets.UTF_8));
            var input = new BufferedInputStream(socket.getInputStream());
            assertEquals("FRAME_TOO_LARGE", ProtocolCodec.decodeResponse(FrameIO.read(input)).errorCode());
            assertEquals(-1, input.read());
        }
        try (var healthy = client()) {
            healthy.connect();
            assertEquals("PONG", healthy.ping());
        }
    }

    @Test
    void rejectsTruncatedAndInvalidUtf8Connections() throws Exception {
        try (var socket = socket()) {
            socket.getOutputStream().write('{');
            socket.shutdownOutput();
            assertEquals("INCOMPLETE_FRAME", ProtocolCodec.decodeResponse(FrameIO.read(socket.getInputStream())).errorCode());
        }
        try (var socket = socket()) {
            socket.getOutputStream().write(new byte[]{(byte) 0xff, '\n'});
            assertEquals("INVALID_UTF8", ProtocolCodec.decodeResponse(FrameIO.read(socket.getInputStream())).errorCode());
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    void shutdownUnblocksClientsWaitingOnServer() throws Exception {
        try (var socket = socket()) {
            FrameIO.write(socket.getOutputStream(), ping("before-close"));
            assertNotNull(FrameIO.read(socket.getInputStream()));
            server.close();
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    void clientCloseUnblocksAnExchangeWithoutWaitingForItsMonitor() throws Exception {
        try (var fake = new ServerSocket(0); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var client = new SupplyGoTcpClient("127.0.0.1", fake.getLocalPort());
            client.connect();
            try (var peer = fake.accept()) {
                Future<String> waiting = executor.submit(client::ping);
                assertNotNull(FrameIO.read(peer.getInputStream()));
                client.close();
                assertThrows(java.util.concurrent.ExecutionException.class, () -> waiting.get(1, TimeUnit.SECONDS));
            } finally {
                client.close();
            }
        }
    }

    @Test
    void clientRejectsWrongResponseCorrelationAndCloses() throws Exception {
        try (var fake = new ServerSocket(0); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> peer = executor.submit(() -> {
                try (Socket socket = fake.accept()) {
                    FrameIO.read(socket.getInputStream());
                    FrameIO.write(socket.getOutputStream(), ProtocolCodec.encode(Response.ok("wrong-id", "PONG")));
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
            try (var client = new SupplyGoTcpClient("127.0.0.1", fake.getLocalPort())) {
                client.connect();
                assertEquals("UNEXPECTED_RESPONSE", assertThrows(ProtocolException.class, client::ping).code());
                assertThrows(IOException.class, client::ping);
            }
            peer.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void clientTimesOutWhenPeerDoesNotReply() throws Exception {
        try (var fake = new ServerSocket(0); var client = new SupplyGoTcpClient("127.0.0.1", fake.getLocalPort())) {
            client.connect();
            try (var peer = fake.accept()) {
                assertThrows(java.net.SocketTimeoutException.class, client::ping);
                assertThrows(IOException.class, client::ping);
            }
        }
    }

    @Test
    void reportsOccupiedPort() {
        assertThrows(IOException.class, () -> new SupplyGoTcpServer("127.0.0.1", server.port()));
    }

    @Test
    void rejectsConnectionsAboveHandlerLimitWithoutDisruptingAcceptedClients() throws Exception {
        List<Socket> clients = new ArrayList<>();
        try {
            for (int i = 0; i < SupplyGoTcpServer.MAX_CLIENTS; i++) {
                Socket socket = socket();
                clients.add(socket);
                FrameIO.write(socket.getOutputStream(), ping("limit-" + i));
                assertNotNull(FrameIO.read(socket.getInputStream()));
            }
            try (Socket extra = socket()) {
                assertEquals(-1, extra.getInputStream().read());
            }
            Socket first = clients.getFirst();
            FrameIO.write(first.getOutputStream(), ping("still-alive"));
            assertEquals("still-alive", ProtocolCodec.decodeResponse(FrameIO.read(first.getInputStream())).requestId());
        } finally {
            for (Socket socket : clients) {
                socket.close();
            }
        }
    }

    @Test
    void handlerReportsIdleTimeoutAndClosesConnection() throws Exception {
        try (var listener = new ServerSocket(0); var peer = new Socket("127.0.0.1", listener.getLocalPort());
                var accepted = listener.accept()) {
            peer.setSoTimeout(3000);
            accepted.setSoTimeout(100);
            Thread handler = Thread.ofPlatform().start(new ClientHandler(accepted));
            try {
                assertEquals("IDLE_TIMEOUT", ProtocolCodec.decodeResponse(FrameIO.read(peer.getInputStream())).errorCode());
                assertEquals(-1, peer.getInputStream().read());
            } finally {
                accepted.close();
                handler.join(2000);
                assertFalse(handler.isAlive());
            }
        }
    }

    private SupplyGoTcpClient client() { return new SupplyGoTcpClient("127.0.0.1", server.port()); }

    private Socket socket() throws IOException {
        var socket = new Socket("127.0.0.1", server.port());
        socket.setSoTimeout(3000);
        return socket;
    }

    private static String ping(String id) {
        return "{\"version\":1,\"requestId\":\"" + id + "\",\"type\":\"PING\",\"payload\":{}}";
    }
}
