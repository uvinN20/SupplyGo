package com.supplygo.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/** Immutable endpoint settings shared by the server launcher and client UI. */
public record NetworkConfig(String serverHost, int tcpPort, int udpPort) {
    public static final String HOST_KEY = "supplygo.server.host";
    public static final String TCP_PORT_KEY = "supplygo.tcp.port";
    public static final String UDP_PORT_KEY = "supplygo.udp.port";

    public NetworkConfig {
        if (serverHost == null || serverHost.isBlank()) {
            throw new IllegalArgumentException(HOST_KEY + " must not be blank");
        }
        serverHost = serverHost.strip();
        validatePort(TCP_PORT_KEY, tcpPort);
        validatePort(UDP_PORT_KEY, udpPort);
    }

    public static NetworkConfig load() {
        return load(System.getProperties());
    }

    /** Only the named SupplyGo properties are read from the overrides. */
    public static NetworkConfig load(Properties overrides) {
        Objects.requireNonNull(overrides, "overrides");
        Properties defaults = new Properties();
        try (InputStream input = NetworkConfig.class.getResourceAsStream("/supplygo.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource: supplygo.properties");
            }
            defaults.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read supplygo.properties", exception);
        }

        return new NetworkConfig(
                property(overrides, defaults, HOST_KEY),
                parsePort(TCP_PORT_KEY, property(overrides, defaults, TCP_PORT_KEY)),
                parsePort(UDP_PORT_KEY, property(overrides, defaults, UDP_PORT_KEY)));
    }

    private static String property(Properties overrides, Properties defaults, String key) {
        String value = overrides.getProperty(key, defaults.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value.strip();
    }

    private static int parsePort(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer from 1 to 65535", exception);
        }
    }

    private static void validatePort(String key, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(key + " must be from 1 to 65535");
        }
    }
}
