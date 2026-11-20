package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.mcp.Main;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import com.yoxel.aurinko.mcp.tools.McpTool;
import com.yoxel.aurinko.mcp.tools.ConnectAccountSchemaValidator;
import io.modelcontextprotocol.json.McpJsonDefaults;
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

    public static McpSyncServer create(HttpServletStreamableServerTransportProvider transport, List<McpTool> tools, String instructions) {
        String version = Main.class.getPackage().getImplementationVersion();
        var builder = McpServer.sync(transport)
                .serverInfo("aurinko-mcp", version != null ? version : "development")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .instructions(instructions);
        for (McpTool tool : tools) {
            var specification = tool.specification();
            if ("connect_account".equals(specification.tool().name())) {
                builder.jsonSchemaValidator(new ConnectAccountSchemaValidator(McpJsonDefaults.getSchemaValidator(),
                        specification.tool().inputSchema()));
            }
            builder.tools(specification);
        }
        return builder.build();
    }
}
