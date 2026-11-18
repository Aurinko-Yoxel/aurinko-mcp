package com.yoxel.aurinko.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import lombok.RequiredArgsConstructor;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

@RequiredArgsConstructor
public class PingTool implements McpTool {

    private final ToolDescription toolDescription;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("ping", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "additionalProperties", false))
                        .description(toolDescription.getDescription())
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).build())
                        .build())
                .callHandler((exchange, request) -> McpSchema.CallToolResult.builder()
                        .addTextContent("pong")
                        .isError(false)
                        .build())
                .build();
    }
}
