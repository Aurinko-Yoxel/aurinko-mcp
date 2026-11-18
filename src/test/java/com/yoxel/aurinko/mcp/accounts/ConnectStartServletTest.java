package com.yoxel.aurinko.mcp.accounts;

import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.support.ConnectServletTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectStartServletTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validStateRendersConfirmationAndBrowserCookie(boolean secure) throws Exception {
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user-1");
            return List.of(new AurinkoAccount(42, "IMAP", "alice&bob@example.com", true));
        }, code -> {
            throw new AssertionError("Starting connection must not exchange a code");
        }, secure)) {
            String value = server.getStateManager().createState("user-1", "IMAP");
            var state = server.getStateManager().parseState(value);

            var response = server.get("/accounts/connect?state=" + URLEncoder.encode(value, StandardCharsets.UTF_8), null);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Connect a mailbox to Aurinko", "alice&amp;bob@example.com", "Continue",
                    "accountRole=secondary", "userSignature=", "scopes=Mail.Read");
            assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
            String header = response.headers().firstValue("Set-Cookie").orElseThrow();
            var cookie = HttpCookie.parse(header).get(0);
            assertThat(cookie.getName()).isEqualTo("aurinko_connect_nonce");
            assertThat(cookie.getValue()).isEqualTo(state.getNonce());
            assertThat(cookie.getPath()).isEqualTo("/accounts/callback");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isEqualTo(secure);
            assertThat(header).contains("SameSite=Lax", "Max-Age=600");
        }
    }

    @Test
    void invalidStateRejectedBeforeLoadingAccounts() throws Exception {
        var calls = new AtomicInteger();
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> {
            calls.incrementAndGet();
            return List.of();
        }, code -> {
            throw new AssertionError("Invalid state must not exchange a code");
        })) {
            var response = server.get("/accounts/connect?state=corrupted", null);

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("Invalid connection state");
            assertThat(calls.get()).isZero();
        }
    }
}
