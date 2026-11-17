package com.yoxel.aurinko.mcp.accounts;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.aurinko.AurinkoLogin;
import com.yoxel.aurinko.mcp.support.ConnectServletTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectCallbackServletTest {

    @Test
    void matchingUserCompletesConnectionAndDeletesCookie() throws Exception {
        var calls = new AtomicInteger();
        try (var server = new ConnectServletTestServer(userId -> List.of(), code -> {
            assertThat(code).isEqualTo("provider-code");
            calls.incrementAndGet();
            return new AurinkoLogin("user-1", "private-session", 42);
        })) {
            String state = server.getStateManager().createState("user-1", "IMAP");

            var response = server.get(path(state, "success", "provider-code"), cookie(server, state));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Account connected. You can return to the chat.");
            assertThat(calls.get()).isEqualTo(1);
            assertCookieDeleted(response.headers().firstValue("Set-Cookie").orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong"})
    void missingOrMismatchedCookieRejected(String kind) throws Exception {
        var calls = new AtomicInteger();
        try (var server = new ConnectServletTestServer(userId -> List.of(), code -> {
            calls.incrementAndGet();
            return new AurinkoLogin("user-1", "private-session", 42);
        })) {
            String state = server.getStateManager().createState("user-1", "IMAP");
            String cookie = "missing".equals(kind) ? null : "aurinko_connect_nonce=wrong-nonce";

            var response = server.get(path(state, "success", "provider-code"), cookie);

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(calls.get()).isZero();
            assertCookieDeleted(response.headers().firstValue("Set-Cookie").orElseThrow());
        }
    }

    @Test
    void corruptedStateRejected() throws Exception {
        try (var server = new ConnectServletTestServer(userId -> List.of(), code -> {
            throw new AssertionError("Invalid state must not exchange a code");
        })) {
            var response = server.get(path("corrupted", "success", "provider-code"), null);

            assertThat(response.statusCode()).isEqualTo(400);
            assertCookieDeleted(response.headers().firstValue("Set-Cookie").orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"providerError", "missingCode"})
    void providerErrorOrMissingCodeReturnsFailure(String kind) throws Exception {
        try (var server = new ConnectServletTestServer(userId -> List.of(), code -> {
            throw new AssertionError("Provider failure must not exchange a code");
        })) {
            String state = server.getStateManager().createState("user-1", "CalDav");

            var response = server.get(path(state, "providerError".equals(kind) ? "error" : "success",
                    "missingCode".equals(kind) ? null : "provider-code"), cookie(server, state));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Account was not connected. Please try again from the chat.");
            assertCookieDeleted(response.headers().firstValue("Set-Cookie").orElseThrow());
        }
    }

    @Test
    void differentUserReturnsFailureAndLogsNoUserData() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ConnectCallbackServlet.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try (var server = new ConnectServletTestServer(userId -> List.of(),
                code -> new AurinkoLogin("other-user", "private-session", 42))) {
            String state = server.getStateManager().createState("user-1", "IMAP");

            var response = server.get(path(state, "success", "provider-code"), cookie(server, state));

            assertThat(response.body()).contains("Account was not connected. Please try again from the chat.");
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> assertThat(event.getFormattedMessage())
                    .doesNotContain("user-1", "other-user", "private-session", state));
            assertCookieDeleted(response.headers().firstValue("Set-Cookie").orElseThrow());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void aurinkoFailureReturnsFailurePage() throws Exception {
        try (var server = new ConnectServletTestServer(userId -> List.of(), code -> {
            throw new AurinkoException();
        })) {
            String state = server.getStateManager().createState("user-1", "IMAP");

            var response = server.get(path(state, "success", "provider-code"), cookie(server, state));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Account was not connected. Please try again from the chat.");
            assertCookieDeleted(response.headers().firstValue("Set-Cookie").orElseThrow());
        }
    }

    private String cookie(ConnectServletTestServer server, String state) {
        return "aurinko_connect_nonce=" + server.getStateManager().parseState(state).getNonce();
    }

    private String path(String state, String status, String code) {
        return "/accounts/callback?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&status=" + status + (code == null ? "" : "&code=" + code);
    }

    private void assertCookieDeleted(String header) {
        var cookie = HttpCookie.parse(header).get(0);
        assertThat(cookie.getName()).isEqualTo("aurinko_connect_nonce");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.hasExpired()).isTrue();
    }
}
