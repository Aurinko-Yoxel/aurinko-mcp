package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.support.TestToolDescriptions;
import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountServiceFilterImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import com.yoxel.aurinko.mcp.paging.PageScope;
import com.yoxel.aurinko.mcp.paging.PageCursor;
import com.yoxel.aurinko.mcp.support.FakeAurinkoEmailService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchMessagesToolTest {

    private final McpPrincipal principal = new McpPrincipal("user-1", "private-user-session", "client-1");
    private final FakeAurinkoEmailService emailService = new FakeAurinkoEmailService();
    private final AurinkoAccountService accounts = (ListOnlyAccountService) userId -> {
        assertThat(userId).isEqualTo(principal.getUserId());
        return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true));
    };

    @Test
    void searchUsesSessionAndArgumentsAndReturnsSummaries() throws Exception {
        var result = call(accounts, Map.of("accountId", 42, "query", "from:alice@example.com", "limit", 5));

        assertThat(result.isError()).isFalse();
        assertThat(emailService.getCalls()).isEqualTo(1);
        assertThat(emailService.getUserSession()).isEqualTo(principal.getUserSession());
        assertThat(emailService.getAccountId()).isEqualTo(42);
        assertThat(emailService.getQuery()).isEqualTo("from:alice@example.com");
        assertThat(emailService.getLimit()).isEqualTo(5);
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        var response = McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {});
        assertThat(response).doesNotContainKey("nextCursor");
        var messages = (List<Map<String, Object>>) response.get("items");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).containsEntry("id", "message-1").containsEntry("subject", "private-subject");
        assertThat(text).doesNotContain(principal.getUserSession());
    }

    @Test
    void optionalArgumentsUseDefaults() {
        var result = call(accounts, Map.of("accountId", 42));

        assertThat(result.isError()).isFalse();
        assertThat(emailService.getLimit()).isEqualTo(20);
        assertThat(emailService.getQuery()).isNull();
    }

    @Test
    void foreignAccountRejectedBeforeSearching() {
        var result = call(accounts, Map.of("accountId", 43));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Account not found"));
        assertThat(emailService.getCalls()).isZero();
    }

    @Test
    void unsupportedServiceTypeRejectedBeforeServiceCall() {
        var result = call(new AurinkoAccountServiceFilterImpl((ListOnlyAccountService) userId -> List.of(
                new AurinkoAccount(43, "Salesforce", "user@example.com", true)), List.of("IMAP", "CalDav")), Map.of("accountId", 43));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Account not found"));
        assertThat(emailService.getCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, Integer.MAX_VALUE})
    void outOfRangeLimitRejected(int limit) {
        var result = call(accounts, Map.of("accountId", 42, "limit", limit));

        assertThat(result.isError()).isTrue();
        assertThat(emailService.getCalls()).isZero();
    }

    @Test
    void fractionalLimitRejected() {
        assertThat(call(accounts, Map.of("accountId", 42, "limit", 1.5)).isError()).isTrue();
        assertThat(emailService.getCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "messages"})
    void aurinkoFailureReturnedAndLogsExcludePrivateData(String operation) {
        var failure = new AurinkoException(new IOException("private-user-session private-subject private-body"));
        AurinkoAccountService source = accounts;
        if ("accounts".equals(operation)) {
            source = (ListOnlyAccountService) userId -> {
                throw failure;
            };
        } else {
            emailService.failWith(failure);
        }
        Logger logger = (Logger) LoggerFactory.getLogger(SearchMessagesTool.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = call(source, Map.of("accountId", 42));

            assertThat(result.isError()).isTrue();
            assertThat(result.content()).containsExactly(new McpSchema.TextContent("Failed to load messages from Aurinko"));
            assertThat(emailService.getCalls()).isEqualTo("accounts".equals(operation) ? 0 : 1);
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("private-user-session", "private-subject", "private-body");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void continuationIsEncryptedAndLimitCanChange() throws Exception {
        var position = new PageCursor("private-aurinko-token", 2);
        emailService.setNext(position);
        var arguments = new HashMap<String, Object>(Map.of("accountId", 42));
        arguments.put("limit", 1);
        var first = call(accounts, arguments);
        assertThat(first.isError()).isFalse();
        String text = ((McpSchema.TextContent) first.content().get(0)).text();
        var response = McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {});
        assertThat(response).containsKeys("items", "nextCursor");
        assertThat(text).doesNotContain("private-aurinko-token");
        String cursor = (String) response.get("nextCursor");
        var scope = new PageScope(principal.getUserId(), 42, "search_messages", Map.of("query", ""));
        assertThat(TestToolDescriptions.cursors().parse(cursor, scope)).isEqualTo(position);
        emailService.setNext(null);
        arguments.put("cursor", cursor);
        arguments.put("limit", 50);

        var last = call(accounts, arguments);
        assertThat(last.isError()).isFalse();
        assertThat(emailService.getStart()).isEqualTo(position);
        assertThat(emailService.getLimit()).isEqualTo(50);
        var result = McpJsonDefaults.getMapper().readValue(((McpSchema.TextContent) last.content().get(0)).text(),
                new TypeRef<Map<String, Object>>() {});
        assertThat(result).containsKey("items").doesNotContainKey("nextCursor");
    }

    @Test
    void foreignCursorRejectedBeforeLoadingData() {
        String cursor = TestToolDescriptions.cursors().create(new PageScope("other-user", 42, "search_messages", Map.of("query", "")),
                new PageCursor("private-token", 0));
        var arguments = new HashMap<String, Object>(Map.of("accountId", 42));
        arguments.put("cursor", cursor);
        var result = call(accounts, arguments);
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Invalid or expired cursor"));
        assertThat(emailService.getCalls()).isZero();
    }

    private McpSchema.CallToolResult call(AurinkoAccountService accountService, Map<String, Object> arguments) {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
        var tool = new SearchMessagesTool(new AccountAccessValidator(accountService), emailService, McpJsonDefaults.getMapper(), TestToolDescriptions.tool("search_messages"), TestToolDescriptions.cursors());
        return tool.specification().callHandler().apply(exchange,
                McpSchema.CallToolRequest.builder("search_messages").arguments(arguments).build());
    }
}
