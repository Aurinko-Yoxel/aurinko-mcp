package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RefreshTokenGrantHandlerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private final TokenManagerJweImpl tokenManager = new TokenManagerJweImpl(
            new JweHelper(new SecretKeySpec(new byte[32], "AES"), clock), "https://mcp.example.com/mcp");
    private final GrantHandler handler = new RefreshTokenGrantHandler(tokenManager);

    @Test
    void validRefreshReturnsWorkingTokensAndLeavesOldRefreshValid() {
        String refresh = tokenManager.createRefreshToken(new McpPrincipal("user-1", "user-session", "client-1"));

        var response = handler.handle(Map.of("refresh_token", refresh, "client_id", "client-1"));

        assertThat(response.getRefreshToken()).isNotEqualTo(refresh);
        var principal = new McpPrincipal("user-1", "user-session", "client-1");
        assertThat(tokenManager.verify(response.getAccessToken())).isEqualTo(principal);
        assertThat(tokenManager.verifyRefreshToken(response.getRefreshToken())).isEqualTo(principal);
        assertThat(tokenManager.verifyRefreshToken(refresh)).isEqualTo(principal);
        assertThat(response.getExpiresIn()).isEqualTo(tokenManager.getAccessTokenLifetime().getSeconds());
    }

    @Test
    void differentClientRejected() {
        String refresh = tokenManager.createRefreshToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertError(() -> handler.handle(Map.of("refresh_token", refresh, "client_id", "other-client")), "invalid_grant");
    }

    @Test
    void accessTokenRejectedAsRefresh() {
        String access = tokenManager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertError(() -> handler.handle(Map.of("refresh_token", access, "client_id", "client-1")), "invalid_grant");
    }

    @Test
    void malformedRefreshRejected() {
        assertError(() -> handler.handle(Map.of("refresh_token", "garbage", "client_id", "client-1")), "invalid_grant");
    }

    @ParameterizedTest
    @ValueSource(strings = {"refresh_token", "client_id"})
    void missingRequiredParameterRejected(String missing) {
        var parameters = new HashMap<>(Map.of(
                "refresh_token", tokenManager.createRefreshToken(new McpPrincipal("user-1", "user-session", "client-1")), "client_id", "client-1"));
        parameters.remove(missing);

        assertError(() -> handler.handle(parameters), "invalid_request");
    }

    private void assertError(Runnable action, String error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(OAuthErrorException.class,
                exception -> assertThat(exception.getError()).isEqualTo(error));
    }
}
