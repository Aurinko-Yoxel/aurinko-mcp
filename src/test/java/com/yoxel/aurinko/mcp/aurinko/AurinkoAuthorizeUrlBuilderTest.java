package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AurinkoAuthorizeUrlBuilderTest {

    @Test
    void parametersEncodedWithConfiguredScopes() {
        var config = new AurinkoConfig("https://api.example.com/", "client + &", "secret",
                List.of("Google"), "User.Read Mail.Read");
        AurinkoAuthorizeUrlBuilder builder = builder(config);
        String callback = "https://mcp.example.com/oauth/aurinko/callback?x=1&y=2";

        URI url = URI.create(builder.build("Google", callback, "state + &= юзер"));

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("api.example.com");
        assertThat(url.getPath()).isEqualTo("/v1/auth/authorizeUser");
        assertThat(query(url)).isEqualTo(Map.of("clientId", "client + &", "serviceType", "Google",
                "accountRole", "primary", "responseType", "code", "returnUrl", callback,
                "state", "state + &= юзер", "scopes", "User.Read Mail.Read"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Google", "Office365"})
    void defaultScopesIncluded(String serviceType) {
        var config = new AurinkoConfig("https://api.example.com", "client", "secret", List.of(serviceType), null);
        AurinkoAuthorizeUrlBuilder builder = builder(config);

        URI url = URI.create(builder.build(serviceType, "https://mcp.example.com/callback", "state"));

        assertThat(query(url)).containsEntry("serviceType", serviceType)
                .containsEntry("scopes", "Mail.Read Calendar.ReadWrite");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IMAP", "CalDav"})
    void connectUrlIncludesSignedUserTimestampAndServiceScopes(String serviceType) {
        var config = new AurinkoConfig("https://api.example.com", "client + &", "_-7dzLuqmYh3ZlVEMyIRAA",
                List.of("Google"), null, "Mail.Read Mail.Send", "Calendar.ReadWrite");
        URI url = URI.create(builder(config).buildConnectUrl(serviceType, "user-1", "state + &="));

        assertThat(url.getPath()).isEqualTo("/v1/auth/authorizeUser");
        assertThat(query(url)).hasSize(10).containsEntry("clientId", "client + &")
                .containsEntry("serviceType", serviceType).containsEntry("accountRole", "secondary")
                .containsEntry("responseType", "code").containsEntry("userId", "user-1")
                .containsEntry("timestamp", "1800000000")
                .containsEntry("userSignature", "f8fa1d83f45f4a9eb377dab9727b2e6fe6a671a425b303be48243e4d763523fd")
                .containsEntry("state", "state + &=")
                .containsEntry("returnUrl", "https://mcp.example.com/accounts/callback")
                .containsEntry("scopes", "IMAP".equals(serviceType) ? "Mail.Read Mail.Send" : "Calendar.ReadWrite");
    }

    @ParameterizedTest
    @ValueSource(strings = {"iCloud", "Office365"})
    void otherProvidersUseLoginScopes(String type) {
        var config = new AurinkoConfig("https://api.example.com", "client", "secret", List.of("Google"), "custom.scopes");
        assertThat(query(URI.create(builder(config).buildConnectUrl(type, "user", "state"))))
                .containsEntry("serviceType", type).containsEntry("scopes", "custom.scopes");
    }

    @Test
    void unconfiguredProviderRejected() {
        var config = new AurinkoConfig("https://api.example.com", "client", "secret", List.of("Google"), null);
        assertThatThrownBy(() -> builder(config).buildConnectUrl("Salesforce", "user", "state"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AurinkoAuthorizeUrlBuilder builder(AurinkoConfig config) {
        var serverConfig = new ServerConfig("127.0.0.1", 8080, "https://mcp.example.com/",
                Base64.getEncoder().encodeToString(new byte[32]));
        return new AurinkoAuthorizeUrlBuilderImpl(config, serverConfig, new UserSignatureHelper(config),
                Clock.fixed(Instant.ofEpochSecond(1800000000), ZoneOffset.UTC));
    }

    private Map<String, String> query(URI url) {
        return Arrays.stream(url.getRawQuery().split("&")).map(value -> value.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
