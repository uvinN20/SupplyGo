package com.supplygo.server;

import java.io.IOException;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import com.supplygo.config.NetworkConfig;
import com.supplygo.security.TlsSupport;
import com.supplygo.service.AuthService;
import com.supplygo.storage.AccountRepository;

/** Starts the TCP backend. TLS enables persistent account operations. */
public final class ServerMain {
    private ServerMain() { }
    public static void main(String[] args) {
        Thread shutdownHook = null;
        AccountRepository repository = null;
        com.supplygo.storage.BusinessProfileRepository profileRepository = null;
        com.supplygo.storage.InventoryRepository inventoryRepository = null;
        try {
            NetworkConfig config = NetworkConfig.load();
            boolean tlsEnabled = Boolean.getBoolean("supplygo.tls");
            SSLContext tls = null;
            AuthService authentication = null;
            com.supplygo.service.InventoryService inventory = null;
            com.supplygo.service.BusinessProfileService profiles = null;
            if (tlsEnabled) {
                if (System.getProperty("javax.net.ssl.keyStore", "").isBlank()) {
                    throw new IllegalArgumentException("TLS server requires javax.net.ssl.keyStore; see docs/AUTHENTICATION.md");
                }
                tls = TlsSupport.defaultContext();
                repository = new AccountRepository(Path.of(System.getProperty("supplygo.data.dir", "data")));
                authentication = new AuthService(repository);
                inventoryRepository = new com.supplygo.storage.InventoryRepository(Path.of(System.getProperty("supplygo.data.dir", "data")));
                inventory = new com.supplygo.service.InventoryService(inventoryRepository);
                profileRepository = new com.supplygo.storage.BusinessProfileRepository(Path.of(System.getProperty("supplygo.data.dir", "data")));
                profiles = new com.supplygo.service.BusinessProfileService(profileRepository);
            }
            String bindAddress = System.getProperty("supplygo.server.bind", "127.0.0.1");
            try (SupplyGoTcpServer server = new SupplyGoTcpServer(bindAddress, config.tcpPort(), tls, authentication, inventory, profiles)) {
                AccountRepository store = repository;
                var businessStore = profileRepository;
                com.supplygo.storage.InventoryRepository stock = inventoryRepository;
                shutdownHook = new Thread(() -> {
                    server.close();
                    if (businessStore != null) {
                        try { businessStore.close(); } catch (IOException exception) { System.err.println("Could not close business profiles"); }
                    }
                    if (stock != null) {
                        try { stock.close(); } catch (IOException exception) { System.err.println("Could not close inventory store"); }
                    }
                    if (store != null) {
                        try { store.close(); } catch (IOException exception) { System.err.println("Could not close account store"); }
                    }
                }, "supplygo-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                System.out.printf("SupplyGo TCP server listening on %s:%d (%s)%n", bindAddress, server.port(), tlsEnabled ? "TLS" : "PING/QUIT demo only");
                System.out.println("Press Ctrl+C to stop. UDP is not started yet.");
                server.serve();
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Cannot launch SupplyGo server: " + exception.getMessage());
            System.exit(1);
        } finally {
            if (shutdownHook != null) {
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException ignored) { /* Shutdown hook owns cleanup. */ }
            }
            if (profileRepository != null) {
                try { profileRepository.close(); } catch (IOException exception) { System.err.println("Could not close business profiles"); }
            }
            if (inventoryRepository != null) {
                try { inventoryRepository.close(); } catch (IOException exception) { System.err.println("Could not close inventory store"); }
            }
            if (repository != null) {
                try { repository.close(); } catch (IOException exception) { System.err.println("Could not close account store"); }
            }
        }
    }
}
