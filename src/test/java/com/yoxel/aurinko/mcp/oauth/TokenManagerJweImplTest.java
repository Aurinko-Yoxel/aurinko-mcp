package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.InvalidTokenException;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenManagerJweImplTest {

    private final ServerConfig config = new ServerConfig("127.0.0.1", 8080,
            "https://mcp.example.com", Base64.getEncoder().encodeToString(new byte[32]));
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private final TokenManagerJweImpl manager = new TokenManagerJweImpl(
            new JweHelper(config.getTokenKey(), clock), config.getMcpUrl());

    @Test
    void accessTokenPreservesPrincipal() {
        String token = manager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertThat(manager.verify(token)).isEqualTo(new McpPrincipal("user-1", "user-session", "client-1"));
    }

    @Test
    void expiredAccessTokenRejected() {
        String token = manager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));
        var beforeExpiry = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(),
                Clock.offset(clock, Duration.ofHours(1).minusSeconds(1))), config.getMcpUrl());
        var atExpiry = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(),
                Clock.offset(clock, Duration.ofHours(1))), config.getMcpUrl());

        assertThat(beforeExpiry.verify(token)).isEqualTo(new McpPrincipal("user-1", "user-session", "client-1"));
        assertThatThrownBy(() -> atExpiry.verify(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void differentResourceRejected() {
        var otherConfig = new ServerConfig("127.0.0.1", 8080, "https://other.example.com",
                Base64.getEncoder().encodeToString(new byte[32]));
        var otherManager = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(), clock), otherConfig.getMcpUrl());
        String token = otherManager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertThatThrownBy(() -> manager.verify(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void differentEncryptionKeyRejected() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        var otherConfig = new ServerConfig("127.0.0.1", 8080, config.getPublicUrl(),
                Base64.getEncoder().encodeToString(otherKey));
        var otherManager = new TokenManagerJweImpl(new JweHelper(otherConfig.getTokenKey(), clock), config.getMcpUrl());
        String token = otherManager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertThatThrownBy(() -> manager.verify(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void malformedTokenRejected() {
        assertThatThrownBy(() -> manager.verify("not-a-token")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void clientIdRejectedAsAccessToken() {
        var clients = new ClientRegistrationManagerJweImpl(new JweHelper(config.getTokenKey(), clock));
        String clientId = clients.register(List.of("https://example.com/cb"));

        assertThatThrownBy(() -> manager.verify(clientId)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void authorizationCodeRejectedAsAccessToken() {
        var codes = new AuthorizationCodeManagerJweImpl(new JweHelper(config.getTokenKey(), clock));
        var request = new AuthorizationRequest("client-1", "https://example.com/cb", "state", "challenge",
                config.getMcpUrl());
        String code = codes.createCode(request, "user-1", "user-session");

        assertThatThrownBy(() -> manager.verify(code)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshTokenRejectedAsAccessToken() {
        String refresh = manager.createRefreshToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertThatThrownBy(() -> manager.verify(refresh)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void accessTokenRejectedAsRefreshToken() {
        String access = manager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertThatThrownBy(() -> manager.verifyRefreshToken(access)).isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void refreshTokenExpiresAfterThirtyDays() {
        String refresh = manager.createRefreshToken(new McpPrincipal("user-1", "user-session", "client-1"));
        var beforeExpiry = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(),
                Clock.offset(clock, Duration.ofDays(30).minusSeconds(1))), config.getMcpUrl());
        var atExpiry = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(),
                Clock.offset(clock, Duration.ofDays(30))), config.getMcpUrl());

        assertThat(beforeExpiry.verifyRefreshToken(refresh)).isEqualTo(new McpPrincipal("user-1", "user-session", "client-1"));
        assertThatThrownBy(() -> atExpiry.verifyRefreshToken(refresh)).isInstanceOf(InvalidGrantException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"access", "refresh"})
    void missingUserSessionRejected(String type) {
        var helper = new JweHelper(config.getTokenKey(), clock);
        String token = helper.encrypt(type, Map.of("userId", "user-1", "clientId", "client-1",
                "resource", config.getMcpUrl()), Duration.ofHours(1));

        if ("access".equals(type)) {
            assertThatThrownBy(() -> manager.verify(token)).isInstanceOf(InvalidTokenException.class);
        } else {
            assertThatThrownBy(() -> manager.verifyRefreshToken(token)).isInstanceOf(InvalidGrantException.class);
        }
    }
}
