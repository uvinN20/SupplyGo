package com.supplygo.web;

import com.supplygo.config.NetworkConfig;
import com.supplygo.security.TlsSupport;
import java.io.IOException;

/** Starts the browser HTTP gateway; the TLS TCP service must already be running. */
public final class WebGatewayMain {
    private WebGatewayMain() { }
    public static void main(String[] args) {
        try {
            NetworkConfig config = NetworkConfig.load();
            if (!Boolean.getBoolean("supplygo.tls")) throw new IllegalArgumentException("Web gateway requires -Dsupplygo.tls=true");
            String bind = System.getProperty("supplygo.web.bind", "127.0.0.1");
            int port = Integer.parseInt(System.getProperty("supplygo.web.port", "7080"));
            boolean secureCookie = Boolean.parseBoolean(System.getProperty("supplygo.web.secureCookie", "false"));
            try (WebGatewayServer gateway = new WebGatewayServer(bind, port, config.serverHost(), config.tcpPort(), TlsSupport.defaultContext(), secureCookie)) {
                Runtime.getRuntime().addShutdownHook(new Thread(gateway::close, "supplygo-web-shutdown"));
                gateway.start();
                System.out.printf("SupplyGo web gateway listening on http://%s:%d (upstream TLS verified)%n", bind, gateway.port());
                System.out.println("Keep the TLS TCP server running. Press Ctrl+C to stop the gateway.");
                Thread.currentThread().join();
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("Cannot launch SupplyGo web gateway: " + exception.getMessage());
            System.exit(1);
        }
    }
}
