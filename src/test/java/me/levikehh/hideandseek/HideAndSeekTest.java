package me.levikehh.hideandseek;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

public class HideAndSeekTest {
    static ServerMock server;
    static HideAndSeek plugin;

    @BeforeAll
    public static void beforeAll() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HideAndSeek.class);
    }

    @AfterAll
    public static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void initialize() {
        // Test if server mocking works
        assertNotNull(server, "ServerMock can not be null after setUp");
        // Test if plugin loading works
        assertNotNull(plugin, "Plugin can not be null after setUp");
        assertTrue(plugin.isEnabled(), "Plugin should be enabled");
    }
}
