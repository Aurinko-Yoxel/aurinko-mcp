package com.yoxel.aurinko.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AurinkoConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://127.0.0.1", "http://[::1]", "https://example.com"})
    void secureOrLocalUrlAccepted(String url) {
        var config = AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1",
                "AURINKO_CLIENT_SECRET", "private-secret", "AURINKO_URL", url));

        assertThat(config.getUrl()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "http://localhost.example.com"})
    void remoteHttpUrlRejected(String url) {
        assertThatThrownBy(() -> AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1",
                "AURINKO_CLIENT_SECRET", "private-secret", "AURINKO_URL", url)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AURINKO_URL must use HTTPS unless the host is local");
    }

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
        assertThat(config.getLoginScopes()).isEqualTo("Mail.Read Calendar.ReadWrite");
        assertThat(config.getImapScopes()).isEqualTo("Mail.Read");
        assertThat(config.getCaldavScopes()).isEqualTo("Calendar.ReadWrite");
        assertThat(config.toString()).doesNotContain("private-secret");
    }

    @Test
    void nullLoginScopesInFiveParameterConstructorUseDefault() {
        var config = new AurinkoConfig("https://api.example.com", "client-1", "private-secret",
                List.of("Google"), null);

        assertThat(config.getLoginScopes()).isEqualTo("Mail.Read Calendar.ReadWrite");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankLoginScopesUseDefault(String scopes) {
        var config = AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1",
                "AURINKO_CLIENT_SECRET", "private-secret", "AURINKO_LOGIN_SCOPES", scopes));

        assertThat(config.getLoginScopes()).isEqualTo("Mail.Read Calendar.ReadWrite");
    }

    @Test
    void customLoginScopesTrimmed() {
        var config = AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1",
                "AURINKO_CLIENT_SECRET", "private-secret", "AURINKO_LOGIN_SCOPES", "  User.Read Mail.Read  "));

        assertThat(config.getLoginScopes()).isEqualTo("User.Read Mail.Read");
    }

    @Test
    void customValuesApplied() {
        var config = AurinkoConfig.fromEnvironment(Map.of("AURINKO_CLIENT_ID", "client-1",
                "AURINKO_CLIENT_SECRET", "private-secret", "AURINKO_URL", "https://api.example.com/",
                "AURINKO_LOGIN_SERVICE_TYPES", "Google", "AURINKO_LOGIN_SCOPES", "User.Read Mail.Read",
                "AURINKO_IMAP_SCOPES", "Mail.Read Mail.Send", "AURINKO_CALDAV_SCOPES", "Calendar.Read"));

        assertThat(config.getUrl()).isEqualTo("https://api.example.com");
        assertThat(config.getLoginServiceTypes()).containsExactly("Google");
        assertThat(config.getLoginScopes()).isEqualTo("User.Read Mail.Read");
        assertThat(config.getImapScopes()).isEqualTo("Mail.Read Mail.Send");
        assertThat(config.getCaldavScopes()).isEqualTo("Calendar.Read");
    }
}
