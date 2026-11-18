package com.yoxel.aurinko.mcp.tools;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DisconnectAccountToolTest {

    private final McpPrincipal principal = new McpPrincipal("user-1", "private-session", "client-1");
    private final AccountService accountService = new AccountService();
    private final DisconnectAccountTool tool = new DisconnectAccountTool(new AccountAccessValidator(accountService),
            accountService, McpJsonDefaults.getMapper());

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ownedAccountDisconnectedRegardlessOfActivity(boolean active) throws Exception {
        accountService.active = active;

        var result = call(Map.of("accountId", 42, "userId", "untrusted-user"));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        var value = McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {});
        assertThat(value).containsExactlyInAnyOrderEntriesOf(Map.of("accountId", 42, "disconnected", true));
        assertThat(accountService.operations).containsExactly("list", "delete");
        assertThat(accountService.deletedUserId).isEqualTo(principal.getUserId());
        assertThat(accountService.deletedAccountId).isEqualTo(42);
        assertThat(text).doesNotContain(principal.getUserSession());
    }

    @Test
    void foreignAccountRejectedBeforeDeletion() {
        var result = call(Map.of("accountId", 43));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Account not found"));
        assertThat(accountService.operations).containsExactly("list");
    }

    @Test
    void primaryAccountCannotBeDisconnected() {
        accountService.failure = failure(404);

        var result = call(Map.of("accountId", 42));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent(
                "This account cannot be disconnected. The account used to sign in cannot be removed."));
        assertThat(accountService.operations).containsExactly("list", "delete");
    }

    @Test
    void upstreamFailureReturnedAndLoggedWithoutPrivateDetails() {
        accountService.failure = failure(500);
        Logger logger = (Logger) LoggerFactory.getLogger(DisconnectAccountTool.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = call(Map.of("accountId", 42));

            assertThat(result.isError()).isTrue();
            assertThat(result.content()).containsExactly(new McpSchema.TextContent("Unable to disconnect the account."));
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("httpStatus=500")
                        .doesNotContain("private-session", "private-upstream-details");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void failureWithoutHttpStatusReturnsGeneralError() {
        accountService.failure = new AurinkoException(new IOException("private-upstream-details"));

        var result = call(Map.of("accountId", 42));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Unable to disconnect the account."));
    }

    @Test
    void missingAccountIdRejectedBeforeAccountLookup() {
        var result = call(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("accountId must be an integer"));
        assertThat(accountService.operations).isEmpty();
    }

    @Test
    void annotationsDeclareDestructiveNonIdempotentWrite() {
        var annotations = tool.specification().tool().annotations();

        assertThat(annotations.readOnlyHint()).isFalse();
        assertThat(annotations.destructiveHint()).isTrue();
        assertThat(annotations.idempotentHint()).isFalse();
    }

    private AurinkoException failure(int status) {
        return new AurinkoException(new HttpResponseException.Builder(status, "private-upstream-details", new HttpHeaders())
                .setContent("private-session").build());
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
        return tool.specification().callHandler().apply(exchange,
                McpSchema.CallToolRequest.builder("disconnect_account").arguments(arguments).build());
    }

    private class AccountService implements AurinkoAccountService {

        private final List<String> operations = new ArrayList<>();
        private boolean active = true;
        private AurinkoException failure;
        private String deletedUserId;
        private long deletedAccountId;

        @Override
        public List<AurinkoAccount> listAccounts(String userId) {
            assertThat(userId).isEqualTo(principal.getUserId());
            operations.add("list");
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", active));
        }

        @Override
        public void deleteAccount(String userId, long accountId) {
            operations.add("delete");
            deletedUserId = userId;
            deletedAccountId = accountId;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
