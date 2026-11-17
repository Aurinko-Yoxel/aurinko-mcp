package com.yoxel.aurinko.mcp.tools;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ListAccountsToolTest {

    private final McpPrincipal principal = new McpPrincipal("user-1", "private-session", "client-1");

    @Test
    void accountsReturnedForAuthenticatedUser() throws Exception {
        AurinkoAccountService service = userId -> {
            assertThat(userId).isEqualTo(principal.getUserId());
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true),
                    new AurinkoAccount(43, "CalDAV", "calendar@example.com", false));
        };

        var result = call(service);

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        var accounts = McpJsonDefaults.getMapper().readValue(text, new TypeRef<List<Map<String, Object>>>() {});
        assertThat(accounts).hasSize(2);
        assertThat(((Number) accounts.get(0).get("id")).longValue()).isEqualTo(42);
        assertThat(accounts.get(0)).containsEntry("serviceType", "IMAP")
                .containsEntry("email", "user@example.com").containsEntry("active", true);
        assertThat(accounts.get(1)).containsEntry("serviceType", "CalDAV").containsEntry("active", false);
        assertThat(text).doesNotContain(principal.getUserSession());
    }

    @Test
    void emptyAccountsReturnedAsEmptyArray() {
        var result = call(userId -> List.of());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("[]"));
    }

    @Test
    void aurinkoFailureReturnedWithoutDetails() {
        var result = call(userId -> {
            throw new AurinkoException(new IllegalStateException("Private upstream error details"));
        });

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("Failed to load accounts from Aurinko"));
    }

    private McpSchema.CallToolResult call(AurinkoAccountService service) {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
        var specification = new ListAccountsTool(service, McpJsonDefaults.getMapper()).specification();
        return specification.callHandler().apply(exchange, McpSchema.CallToolRequest.builder("list_accounts")
                .arguments(Map.of("userId", "untrusted-user"))
                .build());
    }
}
