package com.yoxel.aurinko.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServerConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://127.0.0.1", "http://[::1]", "https://example.com"})
    void secureOrLocalUrlAccepted(String url) {
        var config = ServerConfig.fromEnvironment(Map.of("PUBLIC_URL", url,
                "TOKEN_KEY", Base64.getEncoder().encodeToString(new byte[32])));

        assertThat(config.getPublicUrl()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "http://localhost.example.com"})
    void remoteHttpUrlRejected(String url) {
        assertThatThrownBy(() -> ServerConfig.fromEnvironment(Map.of("PUBLIC_URL", url,
                "TOKEN_KEY", Base64.getEncoder().encodeToString(new byte[32]))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PUBLIC_URL must use HTTPS unless the host is local");
    }

    @Test
    void missingTokenKeyRejected() {
        assertThatThrownBy(() -> ServerConfig.fromEnvironment(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TOKEN_KEY is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 16, 31, 33, 64})
    void incorrectTokenKeyLengthRejected(int length) {
        String key = Base64.getEncoder().encodeToString(new byte[length]);

        assertThatThrownBy(() -> ServerConfig.fromEnvironment(Map.of("TOKEN_KEY", key)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TOKEN_KEY")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void malformedBase64TokenKeyRejected() {
        assertThatThrownBy(() -> ServerConfig.fromEnvironment(Map.of("TOKEN_KEY", "invalid!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TOKEN_KEY must be valid base64");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://mcp.example.com", "https://mcp.example.com/"})
    void mcpUrlUsesNormalizedPublicUrl(String publicUrl) {
        var config = ServerConfig.fromEnvironment(Map.of(
                "TOKEN_KEY", Base64.getEncoder().encodeToString(new byte[32]),
                "PUBLIC_URL", publicUrl));

        assertThat(config.getPublicUrl()).isEqualTo("https://mcp.example.com");
        assertThat(config.getMcpUrl()).isEqualTo("https://mcp.example.com/mcp");
        assertThat(config.getTokenKey().getEncoded()).hasSize(32);
    }
}
