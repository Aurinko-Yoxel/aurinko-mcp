package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RefreshTokenGrantHandler implements GrantHandler {

    private final TokenManager tokenManager;

    @Override
    public String getGrantType() {
        return "refresh_token";
    }

    @Override
    public TokenResponse handle(Map<String, String> parameters) throws OAuthErrorException {
        for (String name : List.of("refresh_token", "client_id")) {
            if (parameters.get(name) == null || parameters.get(name).isBlank()) {
                throw new OAuthErrorException("invalid_request", "Missing required parameter: " + name);
            }
        }
        McpPrincipal principal;
        try {
            principal = tokenManager.verifyRefreshToken(parameters.get("refresh_token"));
        } catch (InvalidGrantException e) {
            throw new OAuthErrorException("invalid_grant", "Invalid or expired refresh token");
        }
        if (!principal.getClientId().equals(parameters.get("client_id"))) {
            throw new OAuthErrorException("invalid_grant", "Refresh token client does not match");
        }
        return new TokenResponse(
                tokenManager.createAccessToken(principal),
                tokenManager.createRefreshToken(principal),
                tokenManager.getAccessTokenLifetime().getSeconds());
    }
}
