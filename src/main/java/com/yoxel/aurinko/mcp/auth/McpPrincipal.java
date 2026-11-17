package com.yoxel.aurinko.mcp.auth;

import lombok.ToString;
import lombok.Value;

@Value
public class McpPrincipal {

    String userId;
    @ToString.Exclude
    String userSession;
    String clientId;
}
