package com.yoxel.aurinko.mcp.tools;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import com.yoxel.aurinko.mcp.support.FakeAurinkoEmailService;
import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import com.yoxel.aurinko.mcp.support.TestToolDescriptions;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class SendMessageToolTest {

    private final McpPrincipal principal = new McpPrincipal("user-1", "private-session", "client-1");
    private final FakeAurinkoEmailService service = new FakeAurinkoEmailService();
    private final SendMessageTool tool = new SendMessageTool(new AccountAccessValidator((ListOnlyAccountService) userId -> {
        assertThat(userId).isEqualTo(principal.getUserId());
        return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true));
    }), service, McpJsonDefaults.getMapper(), TestToolDescriptions.tool("send_message"));

    @Test
    void sendsValidatedDraftUsingPrincipalSession() throws Exception {
        var result = call(arguments());
        assertThat(result.isError()).isFalse();
        var value = McpJsonDefaults.getMapper().readValue(((McpSchema.TextContent) result.content().get(0)).text(),
                new TypeRef<Map<String, Object>>() {});
        assertThat(value).isEqualTo(Map.of("id", "sent-message"));
        assertThat(service.getCalls()).isEqualTo(1);
        assertThat(service.getUserSession()).isEqualTo(principal.getUserSession());
        assertThat(service.getAccountId()).isEqualTo(42);
        assertThat(service.getDraft().getTo()).containsExactly("alex@example.com");
        assertThat(service.getDraft().getCc()).containsExactly("copy@example.com");
        assertThat(service.getDraft().getSubject()).isEqualTo("Private subject");
        assertThat(service.getDraft().getBody()).isEqualTo("Plain text <not HTML>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"emptyTo", "badTo", "badCc", "tooMany", "combined", "emptySubject", "blankSubject", "longBody", "missingBody"})
    void invalidDraftRejectedBeforeSending(String kind) {
        var args = arguments();
        switch (kind) {
            case "emptyTo" -> args.put("to", List.of());
            case "badTo" -> args.put("to", List.of("not-an-email"));
            case "badCc" -> args.put("cc", List.of("bad@"));
            case "tooMany" -> args.put("to", addresses(21));
            case "combined" -> {
                args.put("to", addresses(10));
                args.put("cc", addresses(11));
            }
            case "emptySubject" -> args.put("subject", "");
            case "blankSubject" -> args.put("subject", "   ");
            case "longBody" -> args.put("body", "x".repeat(50_001));
            default -> args.remove("body");
        }
        assertThat(call(args).isError()).isTrue();
        assertThat(service.getCalls()).isZero();
    }

    @Test
    void boundaryLimitsAcceptedAndCcOptional() {
        var args = arguments();
        args.put("to", addresses(20));
        args.remove("cc");
        args.put("body", "x".repeat(50_000));
        assertThat(call(args).isError()).isFalse();
        assertThat(service.getDraft().getCc()).isEmpty();
    }

    @Test
    void foreignAccountRejectedBeforeSending() {
        var args = arguments();
        args.put("accountId", 43);
        assertError(call(args), "Account not found");
        assertThat(service.getCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 401})
    void insufficientPermissionRequestsReconnection(int status) {
        service.failWith(new AurinkoException(new HttpResponseException.Builder(status, "private-error", new HttpHeaders())
                .setContent("{\"code\":\"insufficient_scope\",\"message\":\"private-body\"}").build()));
        assertError(call(arguments()), "This account is not allowed to send mail; reconnect it with connect_account");
        assertThat(service.getCalls()).isEqualTo(1);
    }

    @Test
    void timeoutReturnedWithoutRetry() {
        service.failWith(new AurinkoException(new SocketTimeoutException("private-body")));
        assertError(call(arguments()), "Failed to send message; it may have been sent, check before retrying");
        assertThat(service.getCalls()).isEqualTo(1);
    }

    @Test
    void annotationsDeclareDestructiveNonIdempotentWrite() {
        var annotations = tool.specification().tool().annotations();
        assertThat(annotations.readOnlyHint()).isFalse();
        assertThat(annotations.destructiveHint()).isTrue();
        assertThat(annotations.idempotentHint()).isFalse();
    }

    private List<String> addresses(int size) {
        return IntStream.range(0, size).mapToObj(index -> "user" + index + "@example.com").toList();
    }

    private Map<String, Object> arguments() {
        return new HashMap<>(Map.of("accountId", 42, "to", List.of("alex@example.com"), "cc", List.of("copy@example.com"),
                "subject", "Private subject", "body", "Plain text <not HTML>"));
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
        return tool.specification().callHandler().apply(exchange,
                McpSchema.CallToolRequest.builder("send_message").arguments(arguments).build());
    }

    private void assertError(McpSchema.CallToolResult result, String expected) {
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent(expected));
    }
}
