package com.yoxel.aurinko.mcp;

import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
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
        server = Main.createServer(config, aurinkoConfig, aurinkoService, userId -> {
            accountUserId.set(userId);
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true));
        });
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
                    "ping", "list_accounts", "search_messages", "get_message", "list_events", "create_event");
            for (var tool : tools) {
                assertThat(tool.annotations()).isNotNull();
                if ("create_event".equals(tool.name())) {
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
    void missingTokenRejectedOverHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/mcp")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
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
