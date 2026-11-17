package com.yoxel.aurinko.mcp.oauth;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JweHelperTest {

    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private final JweHelper helper = new JweHelper(key, clock);

    @Test
    void encryptedDataPreserved() {
        Map<String, Object> data = Map.of("userId", "user-1", "redirectUris", List.of("https://example.com/cb"));
        String value = helper.encrypt("example", data, Duration.ofMinutes(1));

        assertThat(helper.decrypt(value, "example")).isEqualTo(data);
    }

    @Test
    void differentTypeRejected() {
        String value = helper.encrypt("client", Map.of("id", "value"), null);

        assertThatThrownBy(() -> helper.decrypt(value, "access")).isInstanceOf(JweException.class);
    }

    @Test
    void expiredValueRejected() {
        String value = helper.encrypt("code", Map.of("id", "value"), Duration.ofSeconds(60));
        var later = new JweHelper(key, Clock.offset(clock, Duration.ofSeconds(60)));

        assertThatThrownBy(() -> later.decrypt(value, "code")).isInstanceOf(JweException.class);
    }

    @Test
    void differentKeyRejected() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        var other = new JweHelper(new SecretKeySpec(otherKey, "AES"), clock);
        String value = other.encrypt("code", Map.of("id", "value"), Duration.ofMinutes(1));

        assertThatThrownBy(() -> helper.decrypt(value, "code")).isInstanceOf(JweException.class);
    }

    @Test
    void malformedValueRejected() {
        assertThatThrownBy(() -> helper.decrypt("garbage", "code")).isInstanceOf(JweException.class);
    }
}
