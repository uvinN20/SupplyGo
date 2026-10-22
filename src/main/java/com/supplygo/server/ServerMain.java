package com.supplygo.server;

import java.io.IOException;
import com.supplygo.config.NetworkConfig;

/** Starts the TCP backend. Stop it with Ctrl+C to close all connections. */
public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) {
        Thread shutdownHook = null;
        try {
            NetworkConfig config = NetworkConfig.load();
            String bindAddress = System.getProperty("supplygo.server.bind", "127.0.0.1");
            try (SupplyGoTcpServer server = new SupplyGoTcpServer(bindAddress, config.tcpPort())) {
                shutdownHook = new Thread(server::close, "supplygo-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                System.out.printf("SupplyGo TCP server listening on %s:%d%n", bindAddress, server.port());
                System.out.println("Supports PING and QUIT. Press Ctrl+C to stop. UDP is not started yet.");
                server.serve();
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Cannot launch SupplyGo server: " + exception.getMessage());
            System.exit(1);
        } finally {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown already started; the hook owns cleanup.
                }
            }
        }
    }
}
