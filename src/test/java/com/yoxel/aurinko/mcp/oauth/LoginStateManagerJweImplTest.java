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

public class LoginStateManagerJweImplTest {

    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private final JweHelper helper = new JweHelper(key, clock);
    private final LoginStateManager manager = new LoginStateManagerJweImpl(helper);
    private final LoginState state = new LoginState(new AuthorizationRequest("client-1", "https://example.com/cb",
            "state + &", "challenge", "https://mcp.example.com/mcp"), "nonce-1");

    @Test
    void loginStatePreserved() {
        assertThat(manager.parseState(manager.createState(state))).isEqualTo(state);
    }

    @Test
    void optionalClientStatePreserved() {
        var noState = new LoginState(new AuthorizationRequest("client-1", "https://example.com/cb", null,
                "challenge", "https://mcp.example.com/mcp"), "nonce-1");

        assertThat(manager.parseState(manager.createState(noState))).isEqualTo(noState);
    }

    @Test
    void expiredStateRejected() {
        String value = manager.createState(state);
        var later = new LoginStateManagerJweImpl(new JweHelper(key, Clock.offset(clock, Duration.ofMinutes(10))));

        assertThatThrownBy(() -> later.parseState(value)).isInstanceOf(InvalidLoginStateException.class);
    }

    @Test
    void malformedStateRejected() {
        assertThatThrownBy(() -> manager.parseState("garbage")).isInstanceOf(InvalidLoginStateException.class);
    }

    @Test
    void otherTypeRejected() {
        String value = helper.encrypt("access", Map.of("userId", "user-1"), Duration.ofHours(1));

        assertThatThrownBy(() -> manager.parseState(value)).isInstanceOf(InvalidLoginStateException.class);
    }
}
