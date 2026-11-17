package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.McpPrincipal;

import java.time.Duration;

public interface TokenManager {

    String createAccessToken(McpPrincipal principal);

    String createRefreshToken(McpPrincipal principal);

    McpPrincipal verifyRefreshToken(String token) throws InvalidGrantException;

    Duration getAccessTokenLifetime();
}
