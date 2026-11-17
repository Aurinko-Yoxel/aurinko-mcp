package com.yoxel.aurinko.mcp.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AurinkoConfigTest {

    @Test
    void missingClientIdRejected() {
        assertThatThrownBy(() -> AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_SECRET", "private-secret")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AURINKO_CLIENT_ID is required");
    }

    @Test
    void missingClientSecretRejected() {
        assertThatThrownBy(() -> AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AURINKO_CLIENT_SECRET is required");
    }

    @Test
    void defaultsAppliedWithoutExposingSecret() {
        var config = AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1",
                "AURINKO_CLIENT_SECRET", "private-secret"));

        assertThat(config.getUrl()).isEqualTo("https://api.aurinko.io");
        assertThat(config.getLoginServiceTypes()).containsExactly("Google", "Office365");
        assertThat(config.getLoginScopes()).isNull();
        assertThat(config.toString()).doesNotContain("private-secret");
    }

    @Test
    void customValuesApplied() {
        var config = AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1",
                "AURINKO_CLIENT_SECRET", "private-secret", "AURINKO_URL", "https://api.example.com/",
                "AURINKO_LOGIN_SERVICE_TYPES", "Google", "AURINKO_LOGIN_SCOPES", "User.Read Mail.Read"));

        assertThat(config.getUrl()).isEqualTo("https://api.example.com");
        assertThat(config.getLoginServiceTypes()).containsExactly("Google");
        assertThat(config.getLoginScopes()).isEqualTo("User.Read Mail.Read");
    }
}
