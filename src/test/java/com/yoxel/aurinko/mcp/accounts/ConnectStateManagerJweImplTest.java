package com.yoxel.aurinko.mcp.accounts;

import com.yoxel.aurinko.mcp.oauth.JweHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConnectStateManagerJweImplTest {

    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
    private final JweHelper helper = new JweHelper(key, clock);
    private final ConnectStateManager manager = new ConnectStateManagerJweImpl(helper);

    @Test
    void statePreservesUserAndServiceWithRandomNonce() {
        var state = manager.parseState(manager.createState("user-1", "IMAP"));

        assertThat(state.getUserId()).isEqualTo("user-1");
        assertThat(state.getServiceType()).isEqualTo("IMAP");
        assertThat(state.getNonce()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(state.getNonce())).hasSize(32);
        var other = manager.parseState(manager.createState("user-1", "IMAP"));
        assertThat(other.getNonce()).isNotEqualTo(state.getNonce());
    }

    @Test
    void expiredStateRejected() {
        String state = manager.createState("user-1", "CalDav");
        var later = new ConnectStateManagerJweImpl(new JweHelper(key, Clock.offset(clock, Duration.ofMinutes(10))));

        assertThatThrownBy(() -> later.parseState(state)).isInstanceOf(InvalidConnectStateException.class);
    }

    @Test
    void malformedStateRejected() {
        assertThatThrownBy(() -> manager.parseState("corrupted")).isInstanceOf(InvalidConnectStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "access"})
    void otherTypeRejected(String type) {
        String state = helper.encrypt(type, Map.of("userId", "user-1", "serviceType", "IMAP", "nonce", "nonce"),
                Duration.ofMinutes(10));

        assertThatThrownBy(() -> manager.parseState(state)).isInstanceOf(InvalidConnectStateException.class);
    }
}
