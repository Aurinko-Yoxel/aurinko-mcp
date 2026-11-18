package com.yoxel.aurinko.mcp.oauth;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenServletTest {

    private final AtomicInteger registrations = new AtomicInteger();
    private final AtomicInteger grants = new AtomicInteger();
    private final AtomicReference<Map<String, String>> parameters = new AtomicReference<>();
    private Server server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        var registrationManager = new ClientRegistrationManager() {
            @Override
            public String register(List<String> redirectUris) {
                throw new AssertionError("Unexpected registration");
            }

            @Override
            public ClientRegistration find(String clientId) {
                registrations.incrementAndGet();
                assertThat(clientId).isEqualTo("client-1");
                return new ClientRegistration(List.of("https://example.com/cb"));
            }
        };
        var handler = new GrantHandler() {
            @Override
            public String getGrantType() {
                return "authorization_code";
            }

            @Override
            public TokenResponse handle(Map<String, String> values) {
                grants.incrementAndGet();
                parameters.set(values);
                return new TokenResponse("access-token", "refresh-token", 3600);
            }
        };
        server = new Server();
        var connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        var context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new TokenServlet(registrationManager,
                List.of(handler), McpJsonDefaults.getMapper())), "/oauth/token");
        server.setHandler(context);
        server.start();
        baseUrl = "http://127.0.0.1:" + connector.getLocalPort() + "/oauth/token";
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"code=authorization-code", "refresh_token=refresh-token", "client_id=client-1"})
    void queryParametersRejectedBeforeClientLookupAndGrantHandling(String query) throws Exception {
        var response = post("?" + query);

        assertThat(response.statusCode()).isEqualTo(400);
        var error = McpJsonDefaults.getMapper().readValue(response.body(), new TypeRef<Map<String, Object>>() {});
        assertThat(error).containsEntry("error", "invalid_request").containsKey("error_description");
        assertThat(registrations).hasValue(0);
        assertThat(grants).hasValue(0);
    }

    @Test
    void bodyParametersAccepted() throws Exception {
        var response = post("");

        assertThat(response.statusCode()).isEqualTo(200);
        var tokens = McpJsonDefaults.getMapper().readValue(response.body(), new TypeRef<Map<String, Object>>() {});
        assertThat(tokens).containsEntry("access_token", "access-token")
                .containsEntry("refresh_token", "refresh-token").containsEntry("token_type", "Bearer");
        assertThat(registrations).hasValue(1);
        assertThat(grants).hasValue(1);
        assertThat(parameters.get()).containsEntry("code", "authorization-code")
                .containsEntry("client_id", "client-1").containsEntry("grant_type", "authorization_code");
    }

    private HttpResponse<String> post(String query) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + query))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("client_id=client-1&grant_type=authorization_code&code=authorization-code"))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
