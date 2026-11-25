package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.paging.PageCursorManager;
import com.yoxel.aurinko.mcp.paging.PageResult;
import com.yoxel.aurinko.mcp.paging.PageScope;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.util.LinkedHashMap;

public class ToolResultHelper {

    public static McpSchema.CallToolResult json(McpJsonMapper mapper, Object value) throws IOException {
        return McpSchema.CallToolResult.builder()
                .addTextContent(mapper.writeValueAsString(value))
                .isError(false)
                .build();
    }

    public static McpSchema.CallToolResult page(McpJsonMapper mapper, PageResult<?> page, PageCursorManager manager, PageScope scope)
            throws IOException {
        var value = new LinkedHashMap<String, Object>();
        value.put("items", page.getItems());
        if (page.getNext() != null) {
            value.put("nextCursor", manager.create(scope, page.getNext()));
        }
        return json(mapper, value);
    }

    public static McpSchema.CallToolResult error(String text) {
        return McpSchema.CallToolResult.builder().addTextContent(text).isError(true).build();
    }
}
