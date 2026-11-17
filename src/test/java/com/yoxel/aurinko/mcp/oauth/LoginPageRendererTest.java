package com.yoxel.aurinko.mcp.oauth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginPageRendererTest {

    private final LoginPageRenderer renderer = new LoginPageRendererImpl();
    private final AuthorizationRequest request = new AuthorizationRequest("client-1", "https://client.example.com/cb",
            "state", "challenge", "https://mcp.example.com/mcp");

    @Test
    void hostAndProviderButtonsRendered() {
        String html = renderer.render(request, Map.of("Google", "https://api.example.com/google",
                "Office365", "https://api.example.com/microsoft"));

        assertThat(html).contains("lang=\"en\"", "<h1>Sign in to Aurinko</h1>",
                "The application at <strong>client.example.com</strong> is requesting access.",
                "Sign in with Google", "Sign in with Microsoft",
                "href=\"https://api.example.com/google\"", "href=\"https://api.example.com/microsoft\"");
    }

    @Test
    void externalValuesEscaped() {
        String html = renderer.render(request, Map.of("<script>\"&'", "https://api.example.com/?x=\"<>&'"));

        assertThat(html).doesNotContain("<script>", "x=\"<>");
        assertThat(html).contains("&lt;script&gt;&quot;&amp;&#39;", "?x=&quot;&lt;&gt;&amp;&#39;");
    }

    @Test
    void singleProviderStillRendersPage() {
        String html = renderer.render(request, Map.of("Google", "https://api.example.com/google"));

        assertThat(html).contains("<!doctype html>", "client.example.com", "Sign in with Google");
    }
}
