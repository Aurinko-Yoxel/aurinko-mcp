package com.yoxel.aurinko.mcp.auth;

public interface AccessTokenVerifier {

    McpPrincipal verify(String token) throws InvalidTokenException;
}
