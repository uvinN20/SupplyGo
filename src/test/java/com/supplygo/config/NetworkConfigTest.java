package com.supplygo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NetworkConfigTest {
    @Test
    void loadsPackagedDefaults() {
        NetworkConfig config = NetworkConfig.load(new Properties());
        assertEquals(new NetworkConfig("127.0.0.1", 7070, 7071), config);
    }

    @Test
    void acceptsEndpointOverridesWithoutChangingDefaults() {
        Properties overrides = new Properties();
        overrides.setProperty(NetworkConfig.HOST_KEY, " 192.168.1.10 ");
        overrides.setProperty(NetworkConfig.TCP_PORT_KEY, "8080");
        overrides.setProperty(NetworkConfig.UDP_PORT_KEY, "8081");
        assertEquals(new NetworkConfig("192.168.1.10", 8080, 8081), NetworkConfig.load(overrides));
        assertEquals(7070, NetworkConfig.load(new Properties()).tcpPort());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "3.5", "0", "-1", "65536", "99999999999999"})
    void rejectsInvalidTcpPorts(String port) {
        Properties overrides = new Properties();
        overrides.setProperty(NetworkConfig.TCP_PORT_KEY, port);
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.load(overrides));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "65536", "not-a-port"})
    void rejectsInvalidUdpPorts(String port) {
        Properties overrides = new Properties();
        overrides.setProperty(NetworkConfig.UDP_PORT_KEY, port);
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.load(overrides));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankServerAddresses(String host) {
        Properties overrides = new Properties();
        overrides.setProperty(NetworkConfig.HOST_KEY, host);
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.load(overrides));
    }

    @Test
    void acceptsPortBoundaries() {
        Properties overrides = new Properties();
        overrides.setProperty(NetworkConfig.TCP_PORT_KEY, "1");
        overrides.setProperty(NetworkConfig.UDP_PORT_KEY, "65535");
        NetworkConfig config = NetworkConfig.load(overrides);
        assertEquals(1, config.tcpPort());
        assertEquals(65535, config.udpPort());
    }

    @Test
    void directConstructionAlsoValidatesSettings() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkConfig(" ", 7070, 7071));
        assertThrows(IllegalArgumentException.class, () -> new NetworkConfig("localhost", 0, 7071));
        assertThrows(IllegalArgumentException.class, () -> new NetworkConfig("localhost", 7070, 65536));
    }
}
