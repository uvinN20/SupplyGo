package com.supplygo.network;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import com.supplygo.client.*;
import com.supplygo.model.*;
import com.supplygo.protocol.*;
import com.supplygo.server.SupplyGoTcpServer;
import com.supplygo.service.AuthService;
import com.supplygo.storage.AccountRepository;

@Timeout(30)
class AuthIntegrationTest {
    @TempDir static Path certificates;
    @TempDir Path data;
    static SSLContext serverContext;
    static SSLContext clientContext;
    static final String PASSWORD = "a strong test password";
    AccountRepository repository;
    AuthService authentication;
    com.supplygo.storage.InventoryRepository inventory;
    SupplyGoTcpServer server;
    Thread accept;
    AtomicReference<Throwable> failure = new AtomicReference<>();

    @BeforeAll static void certificate() throws Exception {
        Path store = certificates.resolve("test.p12");
        String secret = UUID.randomUUID().toString();
        String keytool = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", secret, "-keypass", secret).redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("keytool timeout"); }
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(store)) { keys.load(input, secret.toCharArray()); }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, secret.toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keys);
        serverContext = SSLContext.getInstance("TLS"); serverContext.init(managers.getKeyManagers(), null, null);
        clientContext = SSLContext.getInstance("TLS"); clientContext.init(null, trust.getTrustManagers(), null);
    }
    @BeforeEach void start() throws Exception {
        repository = new AccountRepository(data); authentication = new AuthService(repository);
        inventory = new com.supplygo.storage.InventoryRepository(data);
        server = new SupplyGoTcpServer("127.0.0.1", 0, serverContext, authentication, new com.supplygo.service.InventoryService(inventory), new com.supplygo.service.OrderService(inventory));
        accept = Thread.ofPlatform().start(() -> { try { server.serve(); } catch (Throwable e) { failure.set(e); } });
    }
    @AfterEach void stop() throws Exception {
        server.close(); accept.join(3000); assertFalse(accept.isAlive());
        assertNull(failure.get()); repository.close(); inventory.close();
    }
    SupplyGoTcpClient client() { return new SupplyGoTcpClient("localhost", server.port(), clientContext); }

    @Test void ordersOverTlsReserveStockEnforceOwnershipAndCompleteLifecycle() throws Exception {
        authentication.provision("admin", PASSWORD.toCharArray(), Role.ADMIN);
        authentication.provision("seller", PASSWORD.toCharArray(), Role.SELLER);
        authentication.provision("other", PASSWORD.toCharArray(), Role.SELLER);
        try (var manager = client(); var seller = client(); var other = client()) {
            manager.connect(); seller.connect(); other.connect();
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, () -> seller.orderPage(0)).code());
            manager.login("admin", PASSWORD.toCharArray()); seller.login("seller", PASSWORD.toCharArray()); other.login("other", PASSWORD.toCharArray());
            manager.createProduct("APPLE", "Apple"); manager.changeStock(true, "APPLE", 5); manager.changeStock(true, "APPLE", 10);
            String submission = UUID.randomUUID().toString();
            var order = seller.createOrder(submission, "APPLE", 8);
            assertEquals(OrderStatus.PENDING, order.status()); assertEquals("seller", order.sellerUsername());
            assertEquals(order, seller.createOrder(submission, "APPLE", 8));
            assertEquals(1, seller.orderPage(0).getAsJsonArray("orders").size());
            assertTrue(other.orderPage(0).getAsJsonArray("orders").isEmpty());
            assertEquals("ORDER_NOT_FOUND", assertThrows(ServerRejectedException.class, () -> other.getOrder(order.id())).code());
            assertEquals("ORDER_NOT_FOUND", assertThrows(ServerRejectedException.class, () -> other.changeOrder(order.id(), OrderStatus.CANCELLED)).code());
            assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class, () -> seller.changeOrder(order.id(), OrderStatus.APPROVED)).code());
            var product = manager.inventoryPage(false, 0).getAsJsonArray("products").get(0).getAsJsonObject();
            assertEquals(15, product.get("onHand").getAsInt()); assertEquals(8, product.get("reserved").getAsInt()); assertEquals(7, product.get("quantity").getAsInt());
            assertEquals("INSUFFICIENT_STOCK", assertThrows(ServerRejectedException.class, () -> manager.changeStock(false, "APPLE", 8)).code());
            manager.changeOrder(order.id(), OrderStatus.APPROVED); manager.changeOrder(order.id(), OrderStatus.FULFILLED);
            assertEquals(OrderStatus.FULFILLED, seller.getOrder(order.id()).status());
            assertEquals(7, manager.inventoryPage(true, 0).getAsJsonArray("batches").get(0).getAsJsonObject().get("quantity").getAsInt());
            var cancelled = seller.createOrder(UUID.randomUUID().toString(), "APPLE", 7);
            seller.changeOrder(cancelled.id(), OrderStatus.CANCELLED);
            assertEquals(7, manager.inventoryPage(false, 0).getAsJsonArray("products").get(0).getAsJsonObject().get("quantity").getAsInt());
            seller.logout(); assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, () -> seller.getOrder(order.id())).code());
        }
    }

    @Test void orderRoleMatrixIsEnforcedByTheServer() throws Exception {
        var stock = new com.supplygo.service.InventoryService(inventory);
        var admin = new UserProfile("admin", Role.ADMIN); stock.createProduct(admin, "APPLE", "Apple"); stock.receive(admin, "APPLE", 20);
        var orders = new com.supplygo.service.OrderService(inventory);
        for (Role role : Role.values()) {
            String name = role.name().toLowerCase(); authentication.provision(name, PASSWORD.toCharArray(), role);
            var order = orders.create(new UserProfile("seller", Role.SELLER), UUID.randomUUID().toString(), "APPLE", 1);
            try (var peer = client()) {
                peer.connect(); peer.login(name, PASSWORD.toCharArray());
                if (com.supplygo.service.OrderService.isManager(role)) {
                    assertFalse(peer.orderPage(0).getAsJsonArray("orders").isEmpty());
                    assertEquals(OrderStatus.APPROVED, peer.changeOrder(order.id(), OrderStatus.APPROVED).status());
                    assertEquals(OrderStatus.FULFILLED, peer.changeOrder(order.id(), OrderStatus.FULFILLED).status());
                } else if (role == Role.SELLER) {
                    assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class, () -> peer.changeOrder(order.id(), OrderStatus.APPROVED)).code());
                } else {
                    assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class, () -> peer.orderPage(0)).code());
                    assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class, () -> peer.getOrder(order.id())).code());
                }
                if (role != Role.SELLER) assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class,
                        () -> peer.createOrder(UUID.randomUUID().toString(), "APPLE", 1)).code());
                assertEquals("PONG", peer.ping());
            }
        }
    }

    @Test void retryAfterUnreadReplyDoesNotReserveTwice() throws Exception {
        authentication.provision("seller", PASSWORD.toCharArray(), Role.SELLER);
        var stock = new com.supplygo.service.InventoryService(inventory);
        var admin = new UserProfile("admin", Role.ADMIN); stock.createProduct(admin, "APPLE", "Apple"); stock.receive(admin, "APPLE", 10);
        String key = UUID.randomUUID().toString();
        try (SSLSocket raw = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.port())) {
            raw.setSoTimeout(5000);
            JsonObject login = new JsonObject(); login.addProperty("username", "seller"); login.addProperty("password", PASSWORD);
            FrameIO.write(raw.getOutputStream(), ProtocolCodec.encode(Request.create(MessageType.LOGIN, login)));
            assertEquals(Response.Status.OK, ProtocolCodec.decodeResponse(FrameIO.read(raw.getInputStream())).status());
            JsonObject payload = new JsonObject(); payload.addProperty("clientOrderId", key); payload.addProperty("productId", "APPLE"); payload.addProperty("quantity", 7);
            FrameIO.write(raw.getOutputStream(), ProtocolCodec.encode(Request.create(MessageType.CREATE_ORDER, payload)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (inventory.snapshot().orders().isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, inventory.snapshot().orders().size()); // Close without consuming the reply.
        }
        try (var retry = client()) {
            retry.connect(); retry.login("seller", PASSWORD.toCharArray());
            assertEquals(1, retry.createOrder(key, "APPLE", 7).id());
            assertEquals(1, retry.orderPage(0).getAsJsonArray("orders").size());
            assertEquals(3, retry.inventoryPage(false, 0).getAsJsonArray("products").get(0).getAsJsonObject().get("quantity").getAsInt());
        }
    }

    @Test void inventoryOverTlsEnforcesRolesAndPersistsFifoUpdates() throws Exception {
        authentication.provision("manager", PASSWORD.toCharArray(), Role.PRODUCTION_MANAGER);
        try (var manager = client(); var seller = client()) {
            manager.connect(); seller.connect();
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, () -> manager.inventoryPage(false, 0)).code());
            manager.login("manager", PASSWORD.toCharArray());
            manager.createProduct("APPLE", "Apple"); manager.changeStock(true, "APPLE", 5); manager.changeStock(true, "APPLE", 10);
            seller.register("seller", PASSWORD.toCharArray(), Role.SELLER); seller.login("seller", PASSWORD.toCharArray());
            assertEquals(15, seller.inventoryPage(false, 0).getAsJsonArray("products").get(0).getAsJsonObject().get("quantity").getAsInt());
            assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class, () -> seller.changeStock(false, "APPLE", 1)).code());
            manager.changeStock(false, "APPLE", 8);
            var batches = manager.inventoryPage(true, 0).getAsJsonArray("batches");
            assertEquals(1, batches.size()); assertEquals(2, batches.get(0).getAsJsonObject().get("id").getAsInt());
            assertEquals(7, batches.get(0).getAsJsonObject().get("quantity").getAsInt());
            assertEquals("INSUFFICIENT_STOCK", assertThrows(ServerRejectedException.class, () -> manager.changeStock(false, "APPLE", 8)).code());
            assertEquals("PONG", manager.ping()); manager.logout();
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, () -> manager.changeStock(true, "APPLE", 1)).code());
        }
    }

    @Test void registerLoginProfileLogoutAndNewConnection() throws Exception {
        try (var client = client()) {
            client.connect(); assertEquals("PONG", client.ping());
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, client::profile).code());
            assertEquals(Role.SUPPLIER, client.register("supplier", PASSWORD.toCharArray(), Role.SUPPLIER).role());
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, client::profile).code());
            assertEquals(new UserProfile("supplier", Role.SUPPLIER), client.login("SUPPLIER", PASSWORD.toCharArray()));
            assertEquals("supplier", client.profile().username());
            assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class, client::adminUserCount).code());
            assertEquals("PONG", client.ping()); assertEquals("SIGNED_OUT", client.logout());
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, client::profile).code());
            client.login("supplier", PASSWORD.toCharArray()); client.disconnect();
        }
        try (var reconnected = client()) {
            reconnected.connect(); assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, reconnected::profile).code());
        }
    }

    @Test void allRolesAreAuthenticatedAndOnlyAdminMayReadAccountCount() throws Exception {
        for (Role role : Role.values()) authentication.provision(role.name().toLowerCase(), PASSWORD.toCharArray(), role);
        for (Role role : Role.values()) {
            try (var client = client()) {
                client.connect(); assertEquals(role, client.login(role.name().toLowerCase(), PASSWORD.toCharArray()).role());
                if (role == Role.ADMIN) assertEquals(6, client.adminUserCount());
                else assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class, client::adminUserCount).code());
            }
        }
    }

    @Test void privilegeEscalationIsRejectedAndFailedLoginClearsPriorIdentity() throws Exception {
        authentication.provision("admin", PASSWORD.toCharArray(), Role.ADMIN);
        try (var client = client()) {
            client.connect();
            assertEquals("FORBIDDEN", assertThrows(ServerRejectedException.class,
                    () -> client.register("attacker", PASSWORD.toCharArray(), Role.ADMIN)).code());
            client.login("admin", PASSWORD.toCharArray());
            assertEquals("INVALID_CREDENTIALS", assertThrows(ServerRejectedException.class,
                    () -> client.login("admin", "incorrect password".toCharArray())).code());
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, client::adminUserCount).code());
            assertEquals("PONG", client.ping());
        }
        try (SSLSocket raw = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.port())) {
            raw.setSoTimeout(5000);
            JsonObject payload = new JsonObject(); payload.addProperty("username", "admin");
            payload.addProperty("password", PASSWORD); payload.addProperty("role", "ADMIN");
            FrameIO.write(raw.getOutputStream(), ProtocolCodec.encode(Request.create(MessageType.LOGIN, payload)));
            assertEquals("INVALID_MESSAGE", ProtocolCodec.decodeResponse(FrameIO.read(raw.getInputStream())).errorCode());
        }
    }

    @Test void sessionsDoNotLeakBetweenClients() throws Exception {
        authentication.provision("seller", PASSWORD.toCharArray(), Role.SELLER);
        try (var first = client(); var second = client()) {
            first.connect(); second.connect(); first.login("seller", PASSWORD.toCharArray());
            assertEquals("AUTH_REQUIRED", assertThrows(ServerRejectedException.class, second::profile).code());
            second.login("seller", PASSWORD.toCharArray()); first.logout();
            assertEquals("seller", second.profile().username());
        }
    }

    @Test void untrustedCertificatesAndWrongHostnamesAreRejected() throws Exception {
        SSLContext untrusted = SSLContext.getInstance("TLS"); untrusted.init(null, null, null);
        try (var client = new SupplyGoTcpClient("localhost", server.port(), untrusted)) {
            assertThrows(SSLHandshakeException.class, client::connect);
        }
        try (var client = new SupplyGoTcpClient("127.0.0.1", server.port(), clientContext)) {
            assertThrows(SSLHandshakeException.class, client::connect);
        }
    }

    @Test void normalClientNeverSendsCredentialsWithoutTls() throws Exception {
        try (var plaintext = new SupplyGoTcpClient("localhost", server.port())) {
            assertTrue(assertThrows(IOException.class, () -> plaintext.login("seller", PASSWORD.toCharArray())).getMessage().contains("TLS"));
        }
        try (var plainServer = new SupplyGoTcpServer("127.0.0.1", 0)) {
            Thread loop = Thread.ofPlatform().start(() -> { try { plainServer.serve(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (var peer = new java.net.Socket("127.0.0.1", plainServer.port())) {
                peer.setSoTimeout(3000);
                JsonObject payload = new JsonObject(); payload.addProperty("username", "seller"); payload.addProperty("password", PASSWORD);
                FrameIO.write(peer.getOutputStream(), ProtocolCodec.encode(Request.create(MessageType.LOGIN, payload)));
                assertEquals("TLS_REQUIRED", ProtocolCodec.decodeResponse(FrameIO.read(peer.getInputStream())).errorCode());
            } finally { plainServer.close(); loop.join(2000); assertFalse(loop.isAlive()); }
        }
    }
}
