package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.AccessTokenVerifier;
import com.yoxel.aurinko.mcp.auth.InvalidTokenException;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Map;

@RequiredArgsConstructor
public class TokenManagerJweImpl implements TokenManager, AccessTokenVerifier {

    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofHours(1);
    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);

    private final JweHelper jweHelper;
    private final String resource;

    @Override
    public String createAccessToken(McpPrincipal principal) {
        validatePrincipal(principal);
        try {
            return jweHelper.encrypt("access", Map.of(
                    "userId", principal.getUserId(), "userSession", principal.getUserSession(),
                    "clientId", principal.getClientId(), "resource", resource), getAccessTokenLifetime());
        } catch (JweException e) {
            throw new InvalidTokenException(e);
        }
    }

    @Override
    public String createRefreshToken(McpPrincipal principal) {
        validatePrincipal(principal);
        try {
            return jweHelper.encrypt("refresh", Map.of(
                    "userId", principal.getUserId(), "userSession", principal.getUserSession(),
                    "clientId", principal.getClientId(), "resource", resource), REFRESH_TOKEN_LIFETIME);
        } catch (JweException e) {
            throw new InvalidGrantException(e);
        }
    }

    @Override
    public McpPrincipal verifyRefreshToken(String token) throws InvalidGrantException {
        try {
            var data = jweHelper.decrypt(token, "refresh");
            if (!(data.get("userId") instanceof String userId) || userId.isBlank()
                    || !(data.get("userSession") instanceof String userSession) || userSession.isBlank()
                    || !(data.get("clientId") instanceof String clientId) || clientId.isBlank()
                    || !resource.equals(data.get("resource"))) {
                throw new InvalidGrantException();
            }
            return new McpPrincipal(userId, userSession, clientId);
        } catch (JweException e) {
            throw new InvalidGrantException(e);
        }
    }

    @Override
    public Duration getAccessTokenLifetime() {
        return ACCESS_TOKEN_LIFETIME;
    }

    @Override
    public McpPrincipal verify(String token) throws InvalidTokenException {
        try {
            var data = jweHelper.decrypt(token, "access");
            if (!(data.get("userId") instanceof String userId) || userId.isBlank()
                    || !(data.get("userSession") instanceof String userSession) || userSession.isBlank()
                    || !(data.get("clientId") instanceof String clientId) || clientId.isBlank()
                    || !resource.equals(data.get("resource"))) {
                throw new InvalidTokenException();
            }
            return new McpPrincipal(userId, userSession, clientId);
        } catch (JweException e) {
            throw new InvalidTokenException(e);
        }
    }

    private void validatePrincipal(McpPrincipal principal) {
        if (principal == null || principal.getUserId() == null || principal.getUserId().isBlank()
                || principal.getUserSession() == null || principal.getUserSession().isBlank()
                || principal.getClientId() == null || principal.getClientId().isBlank()) {
            throw new IllegalArgumentException("userId, userSession and clientId must not be blank");
        }
    }
}
