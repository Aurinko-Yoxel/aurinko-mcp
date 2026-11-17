package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClientRegistrationManagerJweImplTest {

    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private final JweHelper helper = new JweHelper(key, clock);
    private final ClientRegistrationManagerJweImpl manager = new ClientRegistrationManagerJweImpl(helper);

    @Test
    void registeredRedirectUrisPreservedWithoutExpiry() {
        var uris = List.of("https://example.com/cb", "http://localhost:3000/cb");
        String clientId = manager.register(uris);
        var later = new ClientRegistrationManagerJweImpl(new JweHelper(key,
                Clock.offset(clock, Duration.ofDays(365))));

        assertThat(manager.find(clientId).getRedirectUris()).isEqualTo(uris);
        assertThat(later.find(clientId).getRedirectUris()).isEqualTo(uris);
    }

    @Test
    void malformedClientIdRejected() {
        assertThatThrownBy(() -> manager.find("garbage")).isInstanceOf(InvalidClientException.class);
    }

    @Test
    void accessTokenRejectedAsClientId() {
        var tokens = new TokenManagerJweImpl(helper, "https://mcp.example.com/mcp");
        String token = tokens.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));

        assertThatThrownBy(() -> manager.find(token)).isInstanceOf(InvalidClientException.class);
    }
}
