package com.supplygo.server;

import com.supplygo.config.NetworkConfig;

/** Backend launcher for the scaffold milestone; no socket listener is started yet. */
public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) {
        try {
            NetworkConfig config = NetworkConfig.load();
            System.out.println("SupplyGo | Backend scaffold");
            System.out.printf("Configured server endpoint: %s:%d%n", config.serverHost(), config.tcpPort());
            System.out.printf("Configured UDP port: %d%n", config.udpPort());
            System.out.println("Configuration validated. No TCP or UDP sockets are open in this milestone.");
            System.out.println("Next milestone: TCP client-server request/response communication.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Cannot launch SupplyGo server: " + exception.getMessage());
            System.exit(1);
        }
    }
}
