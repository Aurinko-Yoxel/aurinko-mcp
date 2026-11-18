package com.yoxel.aurinko.mcp.auth;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
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
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class McpSessionOwnerFilterTest {

    private final McpPrincipal principal = new McpPrincipal("user", "private-session", "client");
    private final McpSessionOwner owner = McpSessionOwner.of(principal);
    private final McpSessionOwners owners = new McpSessionOwnersMemoryImpl(Clock.systemUTC());
    private final AtomicInteger calls = new AtomicInteger();
    private final CountDownLatch filterReturned = new CountDownLatch(1);
    private final CountDownLatch completed = new CountDownLatch(1);
    private final HttpClient client = HttpClient.newHttpClient();
    private Server server;
    private URI uri;
    private boolean async;
    private boolean addHeader;
    private int status = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = new Server();
        var connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        var context = new ServletContextHandler();
        context.setContextPath("/");
        var principalFilter = new FilterHolder((Filter) (request, response, chain) -> {
            request.setAttribute(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal);
            chain.doFilter(request, response);
            filterReturned.countDown();
        });
        principalFilter.setAsyncSupported(true);
        context.addFilter(principalFilter, "/mcp", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        var ownerFilter = new FilterHolder(new McpSessionOwnerFilter(owners));
        ownerFilter.setAsyncSupported(true);
        context.addFilter(ownerFilter, "/mcp", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        var servlet = new ServletHolder(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) {
                calls.incrementAndGet();
                if (async) {
                    var asyncContext = request.startAsync();
                    asyncContext.addListener(new AsyncListener() {
                        @Override
                        public void onComplete(AsyncEvent event) {
                            completed.countDown();
                        }

                        @Override
                        public void onTimeout(AsyncEvent event) {
                        }

                        @Override
                        public void onError(AsyncEvent event) {
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) {
                        }
                    });
                    asyncContext.start(() -> {
                        try {
                            if (!filterReturned.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Filter did not return");
                            }
                            respond((HttpServletResponse) asyncContext.getResponse());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            asyncContext.complete();
                        }
                    });
                } else {
                    respond(response);
                }
            }
        });
        servlet.setAsyncSupported(true);
        context.addServlet(servlet, "/mcp");
        server.setHandler(context);
        server.start();
        uri = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/mcp");
    }

    private void respond(HttpServletResponse response) {
        response.setStatus(status);
        if (addHeader) {
            response.addHeader("mcp-session-id", "created-session");
        } else {
            response.setHeader("Mcp-Session-Id", "created-session");
        }
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void foreignSessionRejected() throws Exception {
        owners.register("session", new McpSessionOwner("other-user", "client"));

        var response = send("POST", "session");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void unknownSessionRejected() throws Exception {
        var response = send("POST", "unknown");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void ownedSessionPassedThrough() throws Exception {
        owners.register("session", owner);

        var response = send("POST", "session");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(1);
        assertThat(owners.isOwner("created-session", owner)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responseHeaderRegistersSession(boolean useAddHeader) throws Exception {
        addHeader = useAddHeader;

        var response = send("POST", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(1);
        assertThat(owners.isOwner("created-session", owner)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asynchronousResponseHeaderRegistersSession(boolean useAddHeader) throws Exception {
        async = true;
        addHeader = useAddHeader;

        var response = send("POST", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).hasValue("created-session");
        assertThat(owners.isOwner("created-session", owner)).isTrue();
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulDeleteRemovesSession(boolean asynchronous) throws Exception {
        async = asynchronous;
        status = 204;
        owners.register("session", owner);

        var response = send("DELETE", "session");

        assertThat(response.statusCode()).isEqualTo(204);
        if (async) {
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(owners.isOwner("session", owner)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unsuccessfulDeleteKeepsSession(boolean asynchronous) throws Exception {
        async = asynchronous;
        status = 500;
        owners.register("session", owner);

        var response = send("DELETE", "session");

        assertThat(response.statusCode()).isEqualTo(500);
        if (async) {
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(owners.isOwner("session", owner)).isTrue();
    }

    private HttpResponse<String> send(String method, String sessionId) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
