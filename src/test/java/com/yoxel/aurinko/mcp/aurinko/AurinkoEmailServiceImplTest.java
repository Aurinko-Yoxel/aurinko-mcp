package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.support.FakeEmailServer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class AurinkoEmailServiceImplTest {

    private final String message = """
            {"id":"message-1","threadId":"thread-1","subject":"Mail subject",
            "from":{"name":"Alice","address":"alice@example.com"},
            "to":[{"name":"Bob","address":"bob@example.com"},{"address":"other@example.com"}],
            "cc":[{"name":"Carol","address":"carol@example.com"}],
            "receivedAt":"2026-11-17T10:00:00Z","hasAttachments":true,"body":"Mail body"}
            """;

    @Test
    void queryPassedAndResultsLimitedWithoutFetchingNextPage() throws Exception {
        try (var fake = new FakeEmailServer()) {
            fake.respond(200, "{\"records\":[" + message + "," + message
                    + "],\"nextPageToken\":\"another-page\"}");
            var service = new AurinkoEmailServiceImpl(new AurinkoClientFactoryImpl(fake.config()));
            String query = "from:alice@example.com after:01/01/2026 & subject:hello";

            var results = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> service.searchMessages("private-session", 42, query, 1));

            assertThat(results).hasSize(1);
            assertThat(fake.getRequests()).isEqualTo(1);
            var parameters = Arrays.stream(fake.getRequestUri().getRawQuery().split("&"))
                    .map(value -> value.split("=", 2)).collect(Collectors.toMap(pair -> pair[0],
                            pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            assertThat(parameters).containsEntry("q", query).doesNotContainKey("pageToken");
            assertThat(results.get(0).getFrom()).isEqualTo("Alice <alice@example.com>");
            assertThat(results.get(0).getTo()).containsExactly("Bob <bob@example.com>", "other@example.com");
            assertThat(results.get(0).getId()).isEqualTo("message-1");
            assertThat(results.get(0).getThreadId()).isEqualTo("thread-1");
            assertThat(results.get(0).getSubject()).isEqualTo("Mail subject");
            assertThat(results.get(0).isHasAttachments()).isTrue();
            assertThat(Instant.parse(results.get(0).getReceivedAt())).isEqualTo(Instant.parse("2026-11-17T10:00:00Z"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankQueryDoesNotSendQ(String query) throws Exception {
        try (var fake = new FakeEmailServer()) {
            var service = new AurinkoEmailServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            assertThat(service.searchMessages("private-session", 42, query, 20)).isEmpty();
            assertThat(fake.getRequestUri().getRawQuery()).isNull();
        }
    }

    @Test
    void messageReadWithBodyAndRecipients() throws Exception {
        try (var fake = new FakeEmailServer()) {
            fake.respond(200, message);
            var service = new AurinkoEmailServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            var result = service.getMessage("private-session", 42, "message-1");

            assertThat(fake.getRequestUri().getPath()).isEqualTo("/v1/email/messages/message-1");
            assertThat(result.getBody()).isEqualTo("Mail body");
            assertThat(result.isBodyTruncated()).isFalse();
            assertThat(result.getCc()).containsExactly("Carol <carol@example.com>");
            assertThat(result.getSubject()).isEqualTo("Mail subject");
        }
    }

    @Test
    void longBodyTruncated() throws Exception {
        try (var fake = new FakeEmailServer()) {
            String body = "x".repeat(50_001);
            fake.respond(200, McpJsonDefaults.getMapper().writeValueAsString(Map.of(
                    "id", "message-1", "body", body)));
            var service = new AurinkoEmailServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            var result = service.getMessage("private-session", 42, "message-1");

            assertThat(result.getBody()).isEqualTo(body.substring(0, 50_000));
            assertThat(result.isBodyTruncated()).isTrue();
            assertThat(result.getTo()).isEqualTo(List.of());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"search", "read"})
    void upstreamFailureTranslated(String operation) throws Exception {
        try (var fake = new FakeEmailServer()) {
            fake.respond(500, "{\"code\":\"error\",\"message\":\"Upstream failure\"}");
            var service = new AurinkoEmailServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            if ("search".equals(operation)) {
                assertThatThrownBy(() -> service.searchMessages("private-session", 42, "query", 20))
                        .isInstanceOf(AurinkoException.class);
            } else {
                assertThatThrownBy(() -> service.getMessage("private-session", 42, "message-1"))
                        .isInstanceOf(AurinkoException.class);
            }
        }
    }
}
