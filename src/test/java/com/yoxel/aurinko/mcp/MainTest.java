package com.yoxel.aurinko.mcp;

import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.mcp.accounts.ConnectStateManagerJweImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.server.AurinkoComponents;
import com.yoxel.aurinko.mcp.server.JettyServerFactory;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import com.yoxel.aurinko.mcp.oauth.TokenManagerJweImpl;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class MainTest {

    private final ServerConfig config = new ServerConfig("127.0.0.1", 0,
            "https://mcp.example.com", Base64.getEncoder().encodeToString(new byte[32]));
    private Server server;
    private String baseUrl;
    private final AtomicReference<String> accountUserId = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        var aurinkoConfig = AurinkoConfig.fromEnvironment(Map.of(
                "AURINKO_CLIENT_ID", "app-client", "AURINKO_CLIENT_SECRET", "app-secret"));
        var aurinkoService = AurinkoService.createWithAppAuth(aurinkoConfig.getUrl(),
                aurinkoConfig.getClientId(), aurinkoConfig.getClientSecret());
        var aurinko = AurinkoComponents.create(aurinkoConfig, aurinkoService, (ListOnlyAccountService) userId -> {
            accountUserId.set(userId);
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true),
                    new AurinkoAccount(43, "Salesforce", "crm@example.com", true));
        });
        server = JettyServerFactory.create(config, aurinkoConfig, aurinko);
        server.start();
        int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void pingOverHttp() throws Exception {
        var manager = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(), Clock.systemUTC()), config.getMcpUrl());
        String token = manager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build();

        try (var client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            var initialized = client.initialize();
            assertThat(initialized.serverInfo().name()).isEqualTo("aurinko-mcp");
            assertThat(initialized.capabilities().tools()).isNotNull();

            var tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                    "ping", "list_accounts", "search_messages", "get_message", "list_events", "create_event", "connect_account", "disconnect_account");
            assertThat(tools).hasSize(8);
            for (var tool : tools) {
                assertThat(tool.annotations()).isNotNull();
                if ("disconnect_account".equals(tool.name())) {
                    assertThat(tool.annotations().readOnlyHint()).isFalse();
                    assertThat(tool.annotations().destructiveHint()).isTrue();
                    assertThat(tool.annotations().idempotentHint()).isFalse();
                } else if (List.of("create_event", "connect_account").contains(tool.name())) {
                    assertThat(tool.annotations().readOnlyHint()).isFalse();
                    assertThat(tool.annotations().destructiveHint()).isFalse();
                } else {
                    assertThat(tool.annotations().readOnlyHint()).isTrue();
                }
            }

            var result = client.callTool(McpSchema.CallToolRequest.builder("ping")
                    .arguments(Map.of())
                    .build());
            assertThat(result.isError()).isFalse();
            assertThat(result.content()).containsExactly(new McpSchema.TextContent("pong"));
        }
    }

    @Test
    void sessionRemainsAccessibleOnlyToItsOwner() throws Exception {
        var manager = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(), Clock.systemUTC()), config.getMcpUrl());
        String ownerToken = manager.createAccessToken(new McpPrincipal("user-1", "session-1", "client-1"));
        String otherToken = manager.createAccessToken(new McpPrincipal("user-2", "session-2", "client-1"));
        var sessionId = new AtomicReference<String>();
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + ownerToken))
                .httpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.build().headers().firstValue("Mcp-Session-Id").ifPresent(sessionId::set))
                .build();

        try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            var ping = McpSchema.CallToolRequest.builder("ping").arguments(Map.of()).build();
            assertThat(client.callTool(ping).content()).containsExactly(new McpSchema.TextContent("pong"));
            assertThat(sessionId.get()).isNotBlank();

            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/mcp"))
                    .header("Authorization", "Bearer " + otherToken)
                    .header("Mcp-Session-Id", sessionId.get())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"tools/call\",\"params\":{\"name\":\"ping\",\"arguments\":{}}}"))
                    .build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).isEmpty();
            var result = client.callTool(ping);
            assertThat(result.isError()).isFalse();
            assertThat(result.content()).containsExactly(new McpSchema.TextContent("pong"));
        }
    }

    @Test
    void missingToolArgumentUsesEnglishMessagesWithRussianDefaultLocale() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"));
            Main.useEnglishMessages();
            server.stop();
            startServer();
            var manager = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(), Clock.systemUTC()), config.getMcpUrl());
            String token = manager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));
            var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                    .endpoint("/mcp")
                    .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                    .build();

            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
                client.initialize();
                var result = client.callTool(McpSchema.CallToolRequest.builder("disconnect_account")
                        .arguments(Map.of()).build());

                assertThat(result.isError()).isTrue();
                assertThat(result.content()).isNotEmpty().allSatisfy(content -> {
                    String text = ((McpSchema.TextContent) content).text();
                    assertThat(text).contains("accountId", "required").doesNotContainPattern("\\p{IsCyrillic}");
                });
            }
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void healthIsPublicAndMcpStillRequiresToken() throws Exception {
        var client = HttpClient.newHttpClient();
        var health = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var mcp = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/mcp"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(mcp.statusCode()).isEqualTo(401);
        assertThat(accountUserId.get()).isNull();
    }

    @Test
    void lowercaseProviderAcceptedOverHttp() throws Exception {
        var manager = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(), Clock.systemUTC()), config.getMcpUrl());
        String token = manager.createAccessToken(new McpPrincipal("user-1", "user-session", "client-1"));
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl).endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token)).build();
        try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            var result = client.callTool(McpSchema.CallToolRequest.builder("connect_account")
                    .arguments(Map.of("serviceType", "icloud")).build());
            assertThat(result.isError()).isFalse();
            assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("accounts/connect?state=");
        }
    }

    @Test
    void missingTokenRejectedOverHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/mcp")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void accountConnectionRoutesAccessibleWithoutBearerToken() throws Exception {
        var manager = new ConnectStateManagerJweImpl(new JweHelper(config.getTokenKey(), Clock.systemUTC()));
        String state = manager.createState("token-user", "IMAP");
        var client = HttpClient.newHttpClient();
        var start = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/accounts/connect?state=" + state)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(start.statusCode()).isEqualTo(200);
        assertThat(start.body()).contains("Connect a mailbox to Aurinko");
        String cookie = "aurinko_connect_nonce=" + manager.parseState(state).getNonce();
        var callback = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/accounts/callback?state=" + state + "&status=error"))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(callback.statusCode()).isEqualTo(200);
        assertThat(callback.body()).contains("Account was not connected. Please try again from the chat.");
    }

    @Test
    void listAccountsUsesPrincipalFromToken() throws Exception {
        var manager = new TokenManagerJweImpl(new JweHelper(config.getTokenKey(), Clock.systemUTC()), config.getMcpUrl());
        var principal = new McpPrincipal("token-user", "private-user-session", "client-1");
        String token = manager.createAccessToken(principal);
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build();

        try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            var result = client.callTool(McpSchema.CallToolRequest.builder("list_accounts").arguments(Map.of()).build());

            assertThat(result.isError()).isFalse();
            assertThat(accountUserId.get()).isEqualTo("token-user");
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            var accounts = McpJsonDefaults.getMapper().readValue(text, new TypeRef<List<Map<String, Object>>>() {});
            assertThat(accounts).hasSize(1);
            assertThat(accounts.get(0)).containsEntry("email", "user@example.com");
            assertThat(text).doesNotContain(principal.getUserSession());
        }
    }

    @Test
    void protectedResourceMetadataAvailableOverHttp() throws Exception {
        var httpClient = HttpClient.newHttpClient();
        for (String path : List.of("/.well-known/oauth-protected-resource",
                "/.well-known/oauth-protected-resource/mcp")) {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                    contentType -> assertThat(contentType).startsWith("application/json"));
            var metadata = McpJsonDefaults.getMapper().readValue(response.body(),
                    new TypeRef<Map<String, Object>>() {});
            assertThat(metadata).containsEntry("resource", config.getMcpUrl())
                    .containsEntry("authorization_servers", List.of(config.getPublicUrl()));
        }
    }
}
