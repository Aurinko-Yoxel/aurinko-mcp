package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthorizationCodeGrantHandlerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
    private final JweHelper helper = new JweHelper(key, clock);
    private final String resource = "https://mcp.example.com/mcp";
    private final String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private final AuthorizationRequest request = new AuthorizationRequest("client-1", "https://example.com/cb",
            "state", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", resource);
    private final AuthorizationCodeManager codeManager = new AuthorizationCodeManagerJweImpl(helper);
    private final TokenManagerJweImpl tokenManager = new TokenManagerJweImpl(helper, resource);
    private final GrantHandler handler = new AuthorizationCodeGrantHandler(codeManager, tokenManager,
            new PkceVerifierS256Impl(), new UsedCodesMemoryImpl(clock));

    @Test
    void validRequestReturnsWorkingTokens() {
        TokenResponse response = handler.handle(parameters());

        assertThat(tokenManager.verify(response.getAccessToken())).isEqualTo(new McpPrincipal("user-1", "user-session", "client-1"));
        assertThat(tokenManager.verifyRefreshToken(response.getRefreshToken())).isEqualTo(new McpPrincipal("user-1", "user-session", "client-1"));
        assertThat(response.getExpiresIn()).isEqualTo(tokenManager.getAccessTokenLifetime().getSeconds());
    }

    @Test
    void resourceMayBeOmitted() {
        var parameters = parameters();
        parameters.remove("resource");

        assertThat(tokenManager.verify(handler.handle(parameters).getAccessToken()).getUserId()).isEqualTo("user-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"code_verifier", "client_id", "redirect_uri", "resource"})
    void mismatchedParameterRejected(String name) {
        var parameters = parameters();
        parameters.put(name, "different-value");

        assertError(() -> handler.handle(parameters), "invalid_grant");
    }

    @Test
    void expiredCodeRejected() {
        var earlier = new JweHelper(key, Clock.offset(clock, Duration.ofSeconds(-60)));
        var parameters = parameters();
        parameters.put("code", new AuthorizationCodeManagerJweImpl(earlier).createCode(request, "user-1", "user-session"));

        assertError(() -> handler.handle(parameters), "invalid_grant");
    }

    @Test
    void malformedCodeRejected() {
        var parameters = parameters();
        parameters.put("code", "garbage");

        assertError(() -> handler.handle(parameters), "invalid_grant");
    }

    @Test
    void refreshTokenRejectedAsCode() {
        var parameters = parameters();
        parameters.put("code", tokenManager.createRefreshToken(new McpPrincipal("user-1", "user-session", "client-1")));

        assertError(() -> handler.handle(parameters), "invalid_grant");
    }

    @Test
    void reusedCodeRejected() {
        var parameters = parameters();
        handler.handle(parameters);

        assertError(() -> handler.handle(parameters), "invalid_grant");
    }

    @Test
    void reencodedCodeRejectedAfterUse() {
        var parameters = parameters();
        handler.handle(parameters);
        parameters.put("code", parameters.get("code") + "==");

        assertError(() -> handler.handle(parameters), "invalid_grant");
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "code_verifier", "client_id", "redirect_uri"})
    void missingRequiredParameterRejected(String name) {
        var parameters = parameters();
        parameters.remove(name);

        assertError(() -> handler.handle(parameters), "invalid_request");
    }

    @Test
    void failedPkceAttemptDoesNotConsumeCode() {
        var parameters = parameters();
        parameters.put("code_verifier", "a".repeat(43));
        assertError(() -> handler.handle(parameters), "invalid_grant");
        parameters.put("code_verifier", verifier);

        assertThat(tokenManager.verify(handler.handle(parameters).getAccessToken()).getUserId()).isEqualTo("user-1");
    }

    private Map<String, String> parameters() {
        return new HashMap<>(Map.of("code", codeManager.createCode(request, "user-1", "user-session"),
                "client_id", request.getClientId(), "redirect_uri", request.getRedirectUri(),
                "code_verifier", verifier, "resource", resource));
    }

    private void assertError(Runnable action, String error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(OAuthErrorException.class,
                exception -> assertThat(exception.getError()).isEqualTo(error));
    }
}
