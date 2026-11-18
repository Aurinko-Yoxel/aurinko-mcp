package com.yoxel.aurinko.mcp.auth;

import lombok.Value;

@Value
public class McpSessionOwner {

    String userId;
    String clientId;

    public static McpSessionOwner of(McpPrincipal principal) {
        return new McpSessionOwner(principal.getUserId(), principal.getClientId());
    }
}
