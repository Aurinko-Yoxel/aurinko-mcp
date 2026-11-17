package com.yoxel.aurinko.mcp.auth;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BearerAuthFilterTest {

    private final ServerConfig config = new ServerConfig("127.0.0.1", 8080,
            "https://mcp.example.com/", Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void missingAuthorizationRejected() throws Exception {
        var exchange = new Exchange(null);
        var filter = new BearerAuthFilter(token -> {
            throw new AssertionError("Verifier must not be called without a bearer token");
        }, config);

        exchange.apply(filter);

        assertThat(exchange.status).isEqualTo(401);
        assertThat(exchange.headers).containsEntry("WWW-Authenticate",
                "Bearer resource_metadata=\"https://mcp.example.com/.well-known/oauth-protected-resource\"");
        assertThat(exchange.proceeded).isFalse();
    }

    @Test
    void nonBearerAuthorizationRejected() throws Exception {
        var exchange = new Exchange("Basic credentials");
        var filter = new BearerAuthFilter(token -> {
            throw new AssertionError("Verifier must not be called for another authentication scheme");
        }, config);

        exchange.apply(filter);

        assertThat(exchange.status).isEqualTo(401);
        assertThat(exchange.proceeded).isFalse();
    }

    @Test
    void invalidTokenRejected() throws Exception {
        var exchange = new Exchange("Bearer opaque-token");
        var filter = new BearerAuthFilter(token -> {
            throw new InvalidTokenException();
        }, config);

        exchange.apply(filter);

        assertThat(exchange.status).isEqualTo(401);
        assertThat(exchange.headers).containsEntry("WWW-Authenticate",
                "Bearer resource_metadata=\"https://mcp.example.com/.well-known/oauth-protected-resource\"");
        assertThat(exchange.proceeded).isFalse();
    }

    @Test
    void verifiedPrincipalPassedToRequest() throws Exception {
        var principal = new McpPrincipal("user-1", "user-session", "client-1");
        var exchange = new Exchange("Bearer opaque-token");
        var filter = new BearerAuthFilter(token -> {
            assertThat(token).isEqualTo("opaque-token");
            return principal;
        }, config);

        exchange.apply(filter);

        assertThat(exchange.status).isEqualTo(200);
        assertThat(exchange.proceeded).isTrue();
        assertThat(exchange.attributes.get(BearerAuthFilter.PRINCIPAL_ATTRIBUTE)).isSameAs(principal);
    }

    private static class Exchange {

        private final String authorization;
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private int status = 200;
        private boolean proceeded;

        private Exchange(String authorization) {
            this.authorization = authorization;
        }

        private void apply(BearerAuthFilter filter) throws Exception {
            var request = proxy(HttpServletRequest.class, (proxy, method, args) -> switch (method.getName()) {
                case "getHeader" -> "Authorization".equals(args[0]) ? authorization : null;
                case "setAttribute" -> {
                    attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getAttribute" -> attributes.get(args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });
            var response = proxy(HttpServletResponse.class, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setStatus" -> status = (int) args[0];
                    case "setHeader" -> headers.put((String) args[0], (String) args[1]);
                    default -> throw new UnsupportedOperationException(method.getName());
                }
                return null;
            });
            filter.doFilter(request, response, (nextRequest, nextResponse) -> {
                proceeded = true;
                assertThat(nextRequest).isSameAs(request);
                assertThat(nextResponse).isSameAs(response);
                assertThat(nextRequest.getAttribute(BearerAuthFilter.PRINCIPAL_ATTRIBUTE)).isNotNull();
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}
