package com.supplygo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void returnsExpectedProjectBanner() {
        assertEquals("SupplyGo - Java project initialized.", Main.banner());
    }
}
