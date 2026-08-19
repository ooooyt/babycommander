package com.ooooyt.babycommander.tool.mcp;

import com.ooooyt.babycommander.config.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpClientManagerTest {

    @Test
    void testGetToolsForUnknownServerReturnsEmpty() {
        McpClientManager manager = new McpClientManager();
        assertTrue(manager.getTools("unknown").isEmpty());
    }

    @Test
    void testDisconnectUnknownServerIsNoOp() {
        McpClientManager manager = new McpClientManager();
        manager.disconnect("unknown");
    }

    @Test
    void testGetToolsAfterDisconnectReturnsEmpty() {
        McpClientManager manager = new McpClientManager();
        manager.disconnect("s1");
        assertTrue(manager.getTools("s1").isEmpty());
    }

    @Test
    void testConnectWithUnsupportedTransportThrows() {
        McpClientManager manager = new McpClientManager();
        AgentConfig.McpServerConfig config = new AgentConfig.McpServerConfig();
        config.transport = "http";
        config.url = "http://localhost:3001";
        assertThrows(IllegalArgumentException.class, () -> manager.connect("s1", config));
    }
}
