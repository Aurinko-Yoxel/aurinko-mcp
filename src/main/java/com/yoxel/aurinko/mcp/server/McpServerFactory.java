package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.mcp.Main;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import com.yoxel.aurinko.mcp.tools.McpTool;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

public class McpServerFactory {

    private McpServerFactory() {
    }

    public static HttpServletStreamableServerTransportProvider createTransport(McpJsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(HttpRoutes.MCP_PATH)
                .contextExtractor(McpPrincipalHelper::toContext)
                .build();
    }

    public static McpSyncServer create(HttpServletStreamableServerTransportProvider transport, List<McpTool> tools) {
        String version = Main.class.getPackage().getImplementationVersion();
        var builder = McpServer.sync(transport)
                .serverInfo("aurinko-mcp", version != null ? version : "development")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build());
        for (McpTool tool : tools) {
            builder.tools(tool.specification());
        }
        return builder.build();
    }
}
