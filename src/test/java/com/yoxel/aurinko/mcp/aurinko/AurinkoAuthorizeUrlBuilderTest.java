package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class AurinkoAuthorizeUrlBuilderTest {

    @Test
    void parametersEncodedWithOptionalScopes() {
        var config = new AurinkoConfig("https://api.example.com/", "client + &", "secret",
                List.of("Google"), "User.Read Mail.Read");
        AurinkoAuthorizeUrlBuilder builder = new AurinkoAuthorizeUrlBuilderImpl(config);
        String callback = "https://mcp.example.com/oauth/aurinko/callback?x=1&y=2";

        URI url = URI.create(builder.build("Google", callback, "state + &= юзер"));

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("api.example.com");
        assertThat(url.getPath()).isEqualTo("/v1/auth/authorizeUser");
        assertThat(query(url)).isEqualTo(Map.of("clientId", "client + &", "serviceType", "Google",
                "accountRole", "primary", "responseType", "code", "returnUrl", callback,
                "state", "state + &= юзер", "scopes", "User.Read Mail.Read"));
    }

    @Test
    void unsetScopesOmitted() {
        var config = new AurinkoConfig("https://api.example.com", "client", "secret", List.of("Office365"), null);
        AurinkoAuthorizeUrlBuilder builder = new AurinkoAuthorizeUrlBuilderImpl(config);

        URI url = URI.create(builder.build("Office365", "https://mcp.example.com/callback", "state"));

        assertThat(query(url)).containsEntry("serviceType", "Office365").doesNotContainKey("scopes");
    }

    private Map<String, String> query(URI url) {
        return Arrays.stream(url.getRawQuery().split("&")).map(value -> value.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
