package com.yoxel.aurinko.mcp.oauth;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthorizationCodeManagerJweImplTest {

    @Test
    void codeContainsAuthorizationDataAndExpiresAfterSixtySeconds() {
        var key = new SecretKeySpec(new byte[32], "AES");
        var clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
        var helper = new JweHelper(key, clock);
        var manager = new AuthorizationCodeManagerJweImpl(helper);
        var request = new AuthorizationRequest("client-1", "https://example.com/cb", "state-1",
                "challenge-1", "https://mcp.example.com/mcp");

        String code = manager.createCode(request, "user-1", "user-session");

        assertThat(helper.decrypt(code, "code")).isEqualTo(Map.of(
                "userId", "user-1", "userSession", "user-session", "clientId", "client-1", "redirectUri", request.getRedirectUri(),
                "codeChallenge", "challenge-1", "resource", request.getResource()));
        assertThat(manager.parseCode(code).getUserSession()).isEqualTo("user-session");
        var beforeExpiry = new JweHelper(key, Clock.offset(clock, Duration.ofSeconds(59)));
        var atExpiry = new JweHelper(key, Clock.offset(clock, Duration.ofSeconds(60)));
        assertThat(beforeExpiry.decrypt(code, "code")).containsEntry("userId", "user-1");
        assertThatThrownBy(() -> atExpiry.decrypt(code, "code")).isInstanceOf(JweException.class);
    }
}
