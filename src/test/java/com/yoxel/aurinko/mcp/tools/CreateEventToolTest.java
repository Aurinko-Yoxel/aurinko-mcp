package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.support.TestToolDescriptions;
import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountServiceFilterImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import com.yoxel.aurinko.mcp.support.FakeAurinkoCalendarService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateEventToolTest {

    private final McpPrincipal principal = new McpPrincipal("user-1", "private-session", "client-1");
    private final FakeAurinkoCalendarService service = new FakeAurinkoCalendarService();
    private final AurinkoAccountService accounts = (ListOnlyAccountService) userId -> {
        assertThat(userId).isEqualTo(principal.getUserId());
        return List.of(new AurinkoAccount(42, "CalDAV", "user@example.com", true));
    };
    private final OffsetDateTime start = OffsetDateTime.parse("2026-11-17T09:00:00+02:00");

    @Test
    void createUsesPrincipalSessionAndReturnsId() throws Exception {
        var arguments = arguments();
        arguments.put("calendarId", "calendar-1");
        arguments.put("description", "Private description");
        arguments.put("location", "Private location");

        var result = call(accounts, arguments);

        assertThat(result.isError()).isFalse();
        assertThat(service.getUserSession()).isEqualTo(principal.getUserSession());
        assertThat(service.getAccountId()).isEqualTo(42);
        assertThat(service.getCalendarId()).isEqualTo("calendar-1");
        assertThat(service.getDraft().getSubject()).isEqualTo("Private subject");
        assertThat(service.getDraft().getDescription()).isEqualTo("Private description");
        assertThat(service.getDraft().getLocation()).isEqualTo("Private location");
        assertThat(service.getDraft().getStart()).isEqualTo(start);
        assertThat(service.getDraft().getEnd()).isEqualTo(start.plusHours(1));
        assertThat(service.getDraft().toString()).doesNotContain("Private subject", "Private description", "Private location");
        var value = McpJsonDefaults.getMapper().readValue(((McpSchema.TextContent) result.content().get(0)).text(),
                new TypeRef<Map<String, Object>>() {});
        assertThat(value).isEqualTo(Map.of("id", "created-event"));
    }

    @Test
    void optionalValuesMayBeOmitted() {
        var result = call(accounts, arguments());

        assertThat(result.isError()).isFalse();
        assertThat(service.getCalendarId()).isNull();
        assertThat(service.getDraft().getDescription()).isNull();
        assertThat(service.getDraft().getLocation()).isNull();
    }

    @Test
    void foreignAccountRejectedBeforeServiceCall() {
        var arguments = arguments();
        arguments.put("accountId", 43);

        assertError(call(accounts, arguments), "Account not found");
        assertThat(service.getCalls()).isZero();
    }

    @Test
    void unsupportedServiceTypeRejectedBeforeServiceCall() {
        var arguments = arguments();
        arguments.put("accountId", 43);

        assertError(call(new AurinkoAccountServiceFilterImpl((ListOnlyAccountService) userId -> List.of(
                new AurinkoAccount(43, "Salesforce", "user@example.com", true)), List.of("IMAP", "CalDav")), arguments), "Account not found");
        assertThat(service.getCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "events"})
    void aurinkoFailureReturned(String operation) {
        AurinkoAccountService source = accounts;
        if ("accounts".equals(operation)) {
            source = (ListOnlyAccountService) userId -> {
                throw new AurinkoException();
            };
        } else {
            service.failWith(new AurinkoException());
        }

        assertError(call(source, arguments()), "Failed to create event in Aurinko");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void nonIncreasingEndRejected(int hours) {
        var arguments = arguments();
        arguments.put("end", start.plusHours(hours).toString());

        assertError(call(accounts, arguments), "end must be after start");
        assertThat(service.getCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankSubjectRejected(String subject) {
        var arguments = arguments();
        arguments.put("subject", subject);

        assertError(call(accounts, arguments), "subject must not be blank");
        assertThat(service.getCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void attendeesAndNotificationChoicePassedToService(boolean notify) {
        var args = arguments();
        args.put("attendees", List.of(Map.of("email", "alex@example.com", "name", "Alex")));
        args.put("notifyAttendees", notify);
        assertThat(call(accounts, args).isError()).isFalse();
        assertThat(service.getDraft().getNotifyAttendees()).isEqualTo(notify);
        assertThat(service.getDraft().getAttendees()).singleElement().satisfies(person -> {
            assertThat(person.getEmail()).isEqualTo("alex@example.com");
            assertThat(person.getName()).isEqualTo("Alex");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingNotify", "invalidEmail", "duplicate", "tooMany"})
    void invalidAttendeesRejectedBeforeCreation(String kind) {
        var args = arguments();
        args.put("notifyAttendees", true);
        args.put("attendees", List.of(Map.of("email", "alex@example.com")));
        String expected;
        switch (kind) {
            case "missingNotify" -> {
                args.remove("notifyAttendees");
                expected = "notifyAttendees is required when attendees are given";
            }
            case "invalidEmail" -> {
                args.put("attendees", List.of(Map.of("email", "bad-email")));
                expected = "Invalid attendee email";
            }
            case "duplicate" -> {
                args.put("attendees", List.of(Map.of("email", "alex@example.com"), Map.of("email", "ALEX@EXAMPLE.COM")));
                expected = "Duplicate attendee email";
            }
            default -> {
                args.put("attendees", IntStream.range(0, 51).mapToObj(i -> Map.of("email", "user" + i + "@example.com")).toList());
                expected = "Too many attendees";
            }
        }
        assertError(call(accounts, args), expected);
        assertThat(service.getCalls()).isZero();
    }

    @Test
    void notificationChoiceOmittedWithoutAttendees() {
        var args = arguments();
        args.put("notifyAttendees", true);
        assertThat(call(accounts, args).isError()).isFalse();
        assertThat(service.getDraft().getNotifyAttendees()).isNull();
        assertThat(service.getDraft().getAttendees()).isEmpty();
    }

    private Map<String, Object> arguments() {
        return new HashMap<>(Map.of("accountId", 42, "subject", "Private subject", "start", start.toString(),
                "end", start.plusHours(1).toString()));
    }

    private McpSchema.CallToolResult call(AurinkoAccountService accountService, Map<String, Object> arguments) {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
        var tool = new CreateEventTool(new AccountAccessValidator(accountService), service, McpJsonDefaults.getMapper(), TestToolDescriptions.tool("create_event"));
        return tool.specification().callHandler().apply(exchange,
                McpSchema.CallToolRequest.builder("create_event").arguments(arguments).build());
    }

    private void assertError(McpSchema.CallToolResult result, String message) {
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent(message));
    }
}
