package com.ooooyt.babycommander.tool.mcp;

import com.ooooyt.babycommander.config.AgentConfig;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class McpClientManager {

    private record ServerConnection(Process process, List<McpToolAdapter> tools) {}

    private final Map<String, ServerConnection> discoveredTools = new ConcurrentHashMap<>();

    public void connect(String serverId, AgentConfig.McpServerConfig config) {
        if (!"stdio".equals(config.transport)) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.MCP_UNSUPPORTED_TRANSPORT, config.transport));
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", config.url);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<McpToolAdapter> tools = discoverToolsFromProcess(process, serverId);
            discoveredTools.put(serverId, new ServerConnection(process, tools));

        } catch (IOException e) {
            throw new RuntimeException(I18n.tr(MessageKey.MCP_CONNECT_FAILED, serverId), e);
        }
    }

    public void disconnect(String serverId) {
        ServerConnection conn = discoveredTools.remove(serverId);
        if (conn != null) {
            conn.process.destroyForcibly();
        }
    }

    public List<McpToolAdapter> getTools(String serverId) {
        ServerConnection conn = discoveredTools.get(serverId);
        return conn != null ? conn.tools() : List.of();
    }

    @PreDestroy
    public void disconnectAll() {
        for (ServerConnection conn : discoveredTools.values()) {
            conn.process.destroyForcibly();
        }
        discoveredTools.clear();
    }

    public int getConnectionCount() {
        return discoveredTools.size();
    }

    private List<McpToolAdapter> discoverToolsFromProcess(Process process, String serverId) {
        List<McpToolAdapter> tools = new ArrayList<>();

        try {
            tools.add(new McpToolAdapter(serverId, "mcp_call", "Call an MCP tool from server " + serverId, input -> {
                try {
                    java.io.OutputStream out = process.getOutputStream();
                    out.write((input + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    java.io.InputStream in = process.getInputStream();
                    byte[] buffer = new byte[4096];
                    int len = in.readNBytes(buffer, 0, 4096);
                    return new String(buffer, 0, len, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return "Error calling MCP tool: " + e.getMessage();
                }
            }));
        } catch (Exception e) {
            return new ArrayList<>();
        }

        return tools;
    }
}
