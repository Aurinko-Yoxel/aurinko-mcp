package com.yoxel.aurinko.mcp.paging;

import com.yoxel.aurinko.mcp.oauth.JweHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PageCursorManagerJweImplTest {

    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
    private final JweHelper helper = new JweHelper(key, clock);
    private final PageCursorManager manager = new PageCursorManagerJweImpl(helper);
    private final PageScope scope = new PageScope("user", 42, "search_messages", Map.of("query", "private-query"));

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"private-aurinko-page-token"})
    void cursorRoundTripPreservesPositionAndEncryptsToken(String pageToken) {
        var cursor = new PageCursor(pageToken, 7);
        String encoded = manager.create(scope, cursor);

        assertThat(manager.parse(encoded, scope)).isEqualTo(cursor);
        assertThat(encoded).doesNotContain("private-aurinko-page-token", "private-query");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "account", "tool", "parameters"})
    void differentScopeRejected(String field) {
        var changed = new PageScope("user".equals(field) ? "other-user" : scope.getUserId(),
                "account".equals(field) ? 43 : scope.getAccountId(),
                "tool".equals(field) ? "list_events" : scope.getTool(),
                "parameters".equals(field) ? Map.of("query", "different") : scope.getParameters());
        String token = manager.create(scope, new PageCursor(null, 0));

        assertInvalid(token, changed, manager);
    }

    @Test
    void expiredCursorRejectedAtOneHour() {
        String token = manager.create(scope, new PageCursor("private-token", 1));
        var later = new PageCursorManagerJweImpl(new JweHelper(key, Clock.offset(clock, Duration.ofHours(1))));

        assertInvalid(token, scope, later);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"private-invalid-token", "  "})
    void malformedCursorRejectedWithoutTokenContents(String token) {
        assertInvalid(token, scope, manager);
    }

    @Test
    void differentTokenTypeRejected() {
        String token = helper.encrypt("access", Map.of("private", "contents"), Duration.ofHours(1));

        assertInvalid(token, scope, manager);
    }

    @Test
    void malformedPayloadRejected() {
        String token = helper.encrypt("page", Map.of("private", "contents"), Duration.ofHours(1));

        assertInvalid(token, scope, manager);
    }

    private void assertInvalid(String token, PageScope expected, PageCursorManager verifier) {
        assertThatThrownBy(() -> verifier.parse(token, expected)).isInstanceOf(InvalidPageCursorException.class)
                .hasMessage("Invalid or expired cursor").hasNoCause();
    }
}
