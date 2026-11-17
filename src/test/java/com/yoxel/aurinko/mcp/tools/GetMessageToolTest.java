package com.yoxel.aurinko.mcp.tools;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
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

import static org.assertj.core.api.Assertions.assertThat;

public class GetMessageToolTest {

    private final McpPrincipal principal = new McpPrincipal("user-1", "private-user-session", "client-1");
    private final FakeAurinkoEmailService emailService = new FakeAurinkoEmailService();
    private final AurinkoAccountService accounts = userId -> {
        assertThat(userId).isEqualTo(principal.getUserId());
        return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true));
    };

    @Test
    void readUsesSessionAndArgumentsAndReturnsMessage() throws Exception {
        var result = call(accounts, Map.of("accountId", 42, "messageId", "message-1"));

        assertThat(result.isError()).isFalse();
        assertThat(emailService.getCalls()).isEqualTo(1);
        assertThat(emailService.getUserSession()).isEqualTo(principal.getUserSession());
        assertThat(emailService.getAccountId()).isEqualTo(42);
        assertThat(emailService.getMessageId()).isEqualTo("message-1");
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        var message = McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {});
        assertThat(message).containsEntry("id", "message-1").containsEntry("subject", "private-subject")
                .containsEntry("body", "private-body").containsEntry("bodyTruncated", false);
        assertThat(text).doesNotContain(principal.getUserSession());
    }

    @Test
    void foreignAccountRejectedBeforeReading() {
        var result = call(accounts, Map.of("accountId", 43, "messageId", "message-1"));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Account not found"));
        assertThat(emailService.getCalls()).isZero();
    }

    @Test
    void blankMessageIdRejected() {
        var result = call(accounts, Map.of("accountId", 42, "messageId", " "));

        assertThat(result.isError()).isTrue();
        assertThat(emailService.getCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "messages"})
    void aurinkoFailureReturnedAndLogsExcludePrivateData(String operation) {
        var failure = new AurinkoException(new IOException("private-user-session private-subject private-body"));
        AurinkoAccountService source = accounts;
        if ("accounts".equals(operation)) {
            source = userId -> {
                throw failure;
            };
        } else {
            emailService.failWith(failure);
        }
        Logger logger = (Logger) LoggerFactory.getLogger(GetMessageTool.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = call(source, Map.of("accountId", 42, "messageId", "message-1"));

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

    private McpSchema.CallToolResult call(AurinkoAccountService accountService, Map<String, Object> arguments) {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
        var tool = new GetMessageTool(new AccountAccessValidator(accountService), emailService, McpJsonDefaults.getMapper());
        return tool.specification().callHandler().apply(exchange,
                McpSchema.CallToolRequest.builder("get_message").arguments(arguments).build());
    }
}
