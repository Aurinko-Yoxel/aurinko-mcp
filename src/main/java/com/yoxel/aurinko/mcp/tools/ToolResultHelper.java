package com.yoxel.aurinko.mcp.tools;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;

public class ToolResultHelper {

    public static McpSchema.CallToolResult json(McpJsonMapper mapper, Object value) throws IOException {
        return McpSchema.CallToolResult.builder()
                .addTextContent(mapper.writeValueAsString(value))
                .isError(false)
                .build();
    }

    public static McpSchema.CallToolResult error(String text) {
        return McpSchema.CallToolResult.builder().addTextContent(text).isError(true).build();
    }
}
