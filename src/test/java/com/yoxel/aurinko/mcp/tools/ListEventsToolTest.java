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
import com.yoxel.aurinko.mcp.paging.PageScope;
import com.yoxel.aurinko.mcp.paging.PageCursor;
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

import static org.assertj.core.api.Assertions.assertThat;

public class ListEventsToolTest {

    private final McpPrincipal principal = new McpPrincipal("user-1", "private-session", "client-1");
    private final FakeAurinkoCalendarService service = new FakeAurinkoCalendarService();
    private final AurinkoAccountService accounts = (ListOnlyAccountService) userId -> {
        assertThat(userId).isEqualTo(principal.getUserId());
        return List.of(new AurinkoAccount(42, "CalDAV", "user@example.com", true));
    };
    private final OffsetDateTime timeMin = OffsetDateTime.parse("2026-11-17T09:00:00+02:00");

    @Test
    void listUsesPrincipalSessionAndReturnsEvents() throws Exception {
        var arguments = arguments();
        arguments.put("calendarId", "calendar-1");
        arguments.put("limit", 5);

        var result = call(accounts, arguments);

        assertThat(result.isError()).isFalse();
        assertThat(service.getUserSession()).isEqualTo(principal.getUserSession());
        assertThat(service.getAccountId()).isEqualTo(42);
        assertThat(service.getCalendarId()).isEqualTo("calendar-1");
        assertThat(service.getTimeMin()).isEqualTo(timeMin);
        assertThat(service.getTimeMax()).isEqualTo(timeMin.plusDays(1));
        assertThat(service.getLimit()).isEqualTo(5);
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        var response = McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {});
        assertThat(response).doesNotContainKey("nextCursor");
        var events = (List<Map<String, Object>>) response.get("items");
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("id", "event-1");
        assertThat(text).doesNotContain(principal.getUserSession());
    }

    @Test
    void optionalValuesUseDefaultsAndSixtyTwoDaysAccepted() {
        var arguments = arguments();
        arguments.put("timeMax", timeMin.plusDays(62).toString());

        var result = call(accounts, arguments);

        assertThat(result.isError()).isFalse();
        assertThat(service.getCalendarId()).isNull();
        assertThat(service.getLimit()).isEqualTo(20);
    }

    @Test
    void foreignAccountRejectedBeforeServiceCall() {
        var arguments = arguments();
        arguments.put("accountId", 43);

        var result = call(accounts, arguments);

        assertError(result, "Account not found");
        assertThat(service.getCalls()).isZero();
    }

    @Test
    void unsupportedServiceTypeRejectedBeforeServiceCall() {
        var arguments = arguments();
        arguments.put("accountId", 43);

        var result = call(new AurinkoAccountServiceFilterImpl((ListOnlyAccountService) userId -> List.of(
                new AurinkoAccount(43, "Salesforce", "user@example.com", true)), List.of("IMAP", "CalDav")), arguments);

        assertError(result, "Account not found");
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

        assertError(call(source, arguments()), "Failed to load events from Aurinko");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void nonIncreasingRangeRejected(int days) {
        var arguments = arguments();
        arguments.put("timeMax", timeMin.plusDays(days).toString());

        assertError(call(accounts, arguments), "timeMax must be after timeMin");
        assertThat(service.getCalls()).isZero();
    }

    @Test
    void rangeLongerThanSixtyTwoDaysRejected() {
        var arguments = arguments();
        arguments.put("timeMax", timeMin.plusDays(62).plusSeconds(1).toString());

        assertError(call(accounts, arguments), "Time range must not exceed 62 days");
        assertThat(service.getCalls()).isZero();
    }

    @Test
    void dateWithoutOffsetRejected() {
        var arguments = arguments();
        arguments.put("timeMin", "2026-11-17T09:00:00");

        assertError(call(accounts, arguments), "timeMin must be an ISO-8601 date-time with offset");
        assertThat(service.getCalls()).isZero();
    }

    private Map<String, Object> arguments() {
        return new HashMap<>(Map.of("accountId", 42, "timeMin", timeMin.toString(), "timeMax", timeMin.plusDays(1).toString()));
    }

    @Test
    void continuationIsEncryptedAndLimitCanChange() throws Exception {
        var position = new PageCursor("private-aurinko-token", 2);
        service.setNext(position);
        var arguments = arguments();
        arguments.put("limit", 1);
        var first = call(accounts, arguments);
        assertThat(first.isError()).isFalse();
        String text = ((McpSchema.TextContent) first.content().get(0)).text();
        var response = McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {});
        assertThat(response).containsKeys("items", "nextCursor");
        assertThat(text).doesNotContain("private-aurinko-token");
        String cursor = (String) response.get("nextCursor");
        var scope = new PageScope(principal.getUserId(), 42, "list_events", Map.of("calendarId", "primary", "timeMin", timeMin.toInstant().toString(), "timeMax", timeMin.plusDays(1).toInstant().toString()));
        assertThat(TestToolDescriptions.cursors().parse(cursor, scope)).isEqualTo(position);
        service.setNext(null);
        arguments.put("cursor", cursor);
        arguments.put("limit", 50);
        arguments.put("timeMin", timeMin.toInstant().toString());
        arguments.put("timeMax", timeMin.plusDays(1).toInstant().toString());
        var last = call(accounts, arguments);
        assertThat(last.isError()).isFalse();
        assertThat(service.getStart()).isEqualTo(position);
        assertThat(service.getLimit()).isEqualTo(50);
        var result = McpJsonDefaults.getMapper().readValue(((McpSchema.TextContent) last.content().get(0)).text(),
                new TypeRef<Map<String, Object>>() {});
        assertThat(result).containsKey("items").doesNotContainKey("nextCursor");
    }

    @Test
    void foreignCursorRejectedBeforeLoadingData() {
        String cursor = TestToolDescriptions.cursors().create(new PageScope("other-user", 42, "list_events", Map.of("calendarId", "primary", "timeMin", timeMin.toInstant().toString(), "timeMax", timeMin.plusDays(1).toInstant().toString())),
                new PageCursor("private-token", 0));
        var arguments = arguments();
        arguments.put("cursor", cursor);
        var result = call(accounts, arguments);
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Invalid or expired cursor"));
        assertThat(service.getCalls()).isZero();
    }

    private McpSchema.CallToolResult call(AurinkoAccountService accountService, Map<String, Object> arguments) {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
        var tool = new ListEventsTool(new AccountAccessValidator(accountService), service, McpJsonDefaults.getMapper(), TestToolDescriptions.tool("list_events"), TestToolDescriptions.cursors());
        return tool.specification().callHandler().apply(exchange,
                McpSchema.CallToolRequest.builder("list_events").arguments(arguments).build());
    }

    private void assertError(McpSchema.CallToolResult result, String message) {
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent(message));
    }
}
