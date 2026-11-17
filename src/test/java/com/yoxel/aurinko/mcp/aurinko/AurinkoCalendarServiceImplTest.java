package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.support.FakeCalendarServer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class AurinkoCalendarServiceImplTest {

    private final OffsetDateTime timeMin = OffsetDateTime.parse("2026-11-17T09:00:00+02:00");
    private final OffsetDateTime timeMax = timeMin.plusDays(1);
    private final String event = """
            {"id":"event-1","subject":"Private subject","description":"Private description","location":"Private location",
            "start":{"dateTime":"2026-11-17T09:00:00+02:00"},"end":{"dateTime":"2026-11-17T10:00:00+02:00"},
            "organizer":{"emailAddress":{"name":"Alice","address":"alice@example.com"}},"htmlLink":"https://example.com/event"}
            """;

    @Test
    void rangeUsesDefaultCalendarAndStopsAtLimit() throws Exception {
        try (var fake = new FakeCalendarServer()) {
            fake.respond(200, "{\"records\":[" + event + "," + event + "],\"nextPageToken\":\"next\"}");
            var service = new AurinkoCalendarServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            var result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> service.listEvents("private-session", 42, null, timeMin, timeMax, 1));

            assertThat(fake.getRequestUri().getPath()).isEqualTo("/v1/calendars/primary/events/range");
            assertThat(query(fake.getRequestUri())).containsEntry("timeMin", timeMin.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .containsEntry("timeMax", timeMax.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).doesNotContainKey("pageToken");
            assertThat(fake.getRequests()).isEqualTo(1);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCalendarId()).isEqualTo("primary");
            assertThat(OffsetDateTime.parse(result.get(0).getStart()).isEqual(timeMin)).isTrue();
            assertThat(result.get(0).getOrganizer()).isEqualTo("Alice <alice@example.com>");
            assertThat(result.get(0).isAllDay()).isFalse();
            assertThat(result.get(0).toString()).doesNotContain("Private subject", "Private description", "Private location");
        }
    }

    @Test
    void nextPageLoadedUntilLimit() throws Exception {
        try (var fake = new FakeCalendarServer()) {
            fake.respondPages("{\"records\":[" + event + "],\"nextPageToken\":\"next-token\"}",
                    "{\"records\":[" + event + "," + event + "],\"nextPageToken\":\"another\"}");
            var service = new AurinkoCalendarServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            var result = service.listEvents("private-session", 42, "calendar-1", timeMin, timeMax, 2);

            assertThat(result).hasSize(2);
            assertThat(fake.getRequests()).isEqualTo(2);
            assertThat(fake.getRequestUri().getPath()).isEqualTo("/v1/calendars/calendar-1/events/range");
            assertThat(query(fake.getRequestUri())).containsEntry("pageToken", "next-token");
        }
    }

    @Test
    void emptyPageStopsDespiteNextToken() throws Exception {
        try (var fake = new FakeCalendarServer()) {
            fake.respond(200, "{\"records\":[],\"nextPageToken\":\"next-token\"}");
            var service = new AurinkoCalendarServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            var result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> service.listEvents("private-session", 42, null, timeMin, timeMax, 20));

            assertThat(result).isEmpty();
            assertThat(fake.getRequests()).isEqualTo(1);
        }
    }

    @Test
    void allDayEventDatesPreserved() throws Exception {
        try (var fake = new FakeCalendarServer()) {
            fake.respond(200, "{\"records\":[{\"id\":\"day-event\",\"start\":{\"dateOnly\":\"2026-11-17\"},"
                    + "\"end\":{\"dateOnly\":\"2026-11-18\"}}]}");
            var service = new AurinkoCalendarServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            var result = service.listEvents("private-session", 42, null, timeMin, timeMax, 20);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isAllDay()).isTrue();
            assertThat(result.get(0).getStart()).isEqualTo("2026-11-17");
            assertThat(result.get(0).getEnd()).isEqualTo("2026-11-18");
        }
    }

    @Test
    void createSendsDraftWithoutAttendees() throws Exception {
        try (var fake = new FakeCalendarServer()) {
            fake.respond(200, "{\"id\":\"created-event\"}");
            var service = new AurinkoCalendarServiceImpl(new AurinkoClientFactoryImpl(fake.config()));
            var draft = new AurinkoEventDraft("Private subject", "Private description", "Private location", timeMin, timeMax);

            String id = service.createEvent("private-session", 42, null, draft);

            assertThat(id).isEqualTo("created-event");
            assertThat(fake.getRequestMethod()).isEqualTo("POST");
            assertThat(fake.getRequestUri().getPath()).isEqualTo("/v1/calendars/primary/events");
            var body = McpJsonDefaults.getMapper().readValue(fake.getRequestBody(), new TypeRef<Map<String, Object>>() {});
            assertThat(body).containsEntry("subject", draft.getSubject()).containsEntry("description", draft.getDescription())
                    .containsEntry("location", draft.getLocation()).doesNotContainKeys("attendees", "meetingInfo");
            assertThat(OffsetDateTime.parse((String) ((Map<?, ?>) body.get("start")).get("dateTime")).isEqual(timeMin)).isTrue();
            assertThat(OffsetDateTime.parse((String) ((Map<?, ?>) body.get("end")).get("dateTime")).isEqual(timeMax)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "create"})
    void upstreamErrorTranslated(String operation) throws Exception {
        try (var fake = new FakeCalendarServer()) {
            fake.respond(500, "{\"code\":\"failed\"}");
            var service = new AurinkoCalendarServiceImpl(new AurinkoClientFactoryImpl(fake.config()));

            if ("list".equals(operation)) {
                assertThatThrownBy(() -> service.listEvents("private-session", 42, null, timeMin, timeMax, 20))
                        .isInstanceOf(AurinkoException.class);
            } else {
                assertThatThrownBy(() -> service.createEvent("private-session", 42, null,
                        new AurinkoEventDraft("subject", null, null, timeMin, timeMax))).isInstanceOf(AurinkoException.class);
            }
        }
    }

    private Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).map(value -> value.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
