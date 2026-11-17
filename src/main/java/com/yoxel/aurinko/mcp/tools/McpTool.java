package com.yoxel.aurinko.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;

public interface McpTool {

    McpServerFeatures.SyncToolSpecification specification();
}
