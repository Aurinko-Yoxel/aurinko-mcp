package com.yoxel.aurinko.mcp.auth;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public class McpPrincipalHelper {

    public static McpTransportContext toContext(HttpServletRequest request) {
        Object principal = request.getAttribute(BearerAuthFilter.PRINCIPAL_ATTRIBUTE);
        return principal instanceof McpPrincipal
                ? McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                : McpTransportContext.EMPTY;
    }

    public static McpPrincipal fromExchange(McpSyncServerExchange exchange) {
        if (exchange != null && exchange.transportContext() != null
                && exchange.transportContext().get(BearerAuthFilter.PRINCIPAL_ATTRIBUTE) instanceof McpPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("MCP principal is missing from transport context");
    }
}
