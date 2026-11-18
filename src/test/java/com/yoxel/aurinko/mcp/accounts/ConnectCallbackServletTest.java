package com.yoxel.aurinko.mcp.accounts;

import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void connectionLinkConsumedEvenWhenAurinkoFails(boolean fail) throws Exception {
        var calls = new AtomicInteger();
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user-1");
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true));
        }, code -> {
            calls.incrementAndGet();
            if (fail) {
                throw new AurinkoException();
            }
            return 42;
        })) {
            String state = server.getStateManager().createState("user-1", "IMAP");
            String cookie = cookie(server, state);

            var first = server.get(path(state, "success", "provider-code"), cookie);
            var repeated = server.get(path(state, "success", "provider-code"), cookie);

            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.body()).contains(fail ? "Account was not connected." : "Account connected.");
            assertThat(repeated.statusCode()).isEqualTo(400);
            assertThat(repeated.body()).isEqualTo("Connection link already used");
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ownedAccountCompletesConnectionAndDeletesCookie(boolean active) throws Exception {
        var calls = new AtomicInteger();
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user-1");
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", active));
        }, code -> {
            assertThat(code).isEqualTo("provider-code");
            calls.incrementAndGet();
            return 42;
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
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> List.of(), code -> {
            calls.incrementAndGet();
            return 42;
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
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> List.of(), code -> {
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
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> List.of(), code -> {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void foreignAccountReturnsFailureAndLogsNoUserData(boolean emptyList) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ConnectCallbackServlet.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user-1");
            return emptyList ? List.of() : List.of(new AurinkoAccount(43, "IMAP", "other@example.com", true));
        },
                code -> 42)) {
            String state = server.getStateManager().createState("user-1", "IMAP");

            var response = server.get(path(state, "success", "provider-code"), cookie(server, state));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Account was not connected. Please try again from the chat.");
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).isEqualTo("Aurinko account connection returned an unexpected user")
                        .doesNotContain("user-1", "other-user", "42", "43", "private-session", state);
            });
            assertCookieDeleted(response.headers().firstValue("Set-Cookie").orElseThrow());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"exchange", "accounts"})
    void aurinkoFailureReturnsFailurePage(String operation) throws Exception {
        var exchanges = new AtomicInteger();
        var lists = new AtomicInteger();
        try (var server = new ConnectServletTestServer((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user-1");
            lists.incrementAndGet();
            throw new AurinkoException();
        }, code -> {
            exchanges.incrementAndGet();
            if ("exchange".equals(operation)) {
                throw new AurinkoException();
            }
            return 42;
        })) {
            String state = server.getStateManager().createState("user-1", "IMAP");

            var response = server.get(path(state, "success", "provider-code"), cookie(server, state));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Account was not connected. Please try again from the chat.");
            assertThat(exchanges.get()).isEqualTo(1);
            assertThat(lists.get()).isEqualTo("accounts".equals(operation) ? 1 : 0);
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
