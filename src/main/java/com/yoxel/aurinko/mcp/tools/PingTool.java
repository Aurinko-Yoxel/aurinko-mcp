package com.yoxel.aurinko.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

public class PingTool implements McpTool {

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("ping", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "additionalProperties", false))
                        .description("Check that the Aurinko MCP server is running")
                        .build())
                .callHandler((exchange, request) -> McpSchema.CallToolResult.builder()
                        .addTextContent("pong")
                        .isError(false)
                        .build())
                .build();
    }
}
