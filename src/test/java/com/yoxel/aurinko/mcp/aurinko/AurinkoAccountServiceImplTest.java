package com.yoxel.aurinko.mcp.aurinko;

import com.sun.net.httpserver.HttpServer;
import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.mcp.support.FakeAurinkoServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class AurinkoAccountServiceImplTest {

    @Test
    void deleteUsesUserAccountPathAndAppAuthentication() throws Exception {
        try (var fake = new FakeAurinkoServer();
                var client = AurinkoService.createWithAppAuth(fake.getUrl(), "app-client", "app-secret")) {
            fake.respond(200, "{}");
            var service = new AurinkoAccountServiceImpl(client);

            service.deleteAccount("user-1", 42);

            assertThat(fake.getRequests()).isEqualTo(1);
            assertThat(fake.getMethod()).isEqualTo("DELETE");
            assertThat(fake.getPath()).isEqualTo("/v1/users/user-1/accounts/42");
            assertThat(fake.getAuthorization()).isEqualTo("Basic " + Base64.getEncoder().encodeToString(
                    "app-client:app-secret".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void deleteErrorPreservesHttpStatus(int status) throws Exception {
        try (var fake = new FakeAurinkoServer();
                var client = AurinkoService.createWithAppAuth(fake.getUrl(), "app-client", "app-secret")) {
            fake.respond(status, "{\"code\":\"error\",\"message\":\"Private upstream error\"}");
            var service = new AurinkoAccountServiceImpl(client);

            assertThatThrownBy(() -> service.deleteAccount("user-1", 42))
                    .isInstanceOfSatisfying(AurinkoException.class,
                            error -> assertThat(error.getStatusCode()).isEqualTo(status));
        }
    }

    @Test
    void emptyPageStopsLoadingEvenWhenNotDone() throws Exception {
        var requests = new AtomicInteger();
        var source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/v1/users/user-1/accounts", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"offset\":0,\"done\":false,\"records\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        source.start();
        try (var client = AurinkoService.createWithAppAuth(
                "http://127.0.0.1:" + source.getAddress().getPort(), "app-client", "app-secret")) {
            var service = new AurinkoAccountServiceImpl(client);

            var accounts = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> service.listAccounts("user-1"));

            assertThat(accounts).isEmpty();
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            source.stop(0);
        }
    }

    @Test
    void infinitePagesLimitedToTwoHundredAccounts() throws Exception {
        var requests = new AtomicInteger();
        var source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/v1/users/user-1/accounts", exchange -> {
            requests.incrementAndGet();
            var parameters = Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                    .map(value -> value.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0],
                            pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            int offset = Integer.parseInt(parameters.get("offset"));
            int limit = Integer.parseInt(parameters.get("limit"));
            String records = IntStream.range(offset, offset + limit)
                    .mapToObj(index -> "{\"id\":" + (index + 1)
                            + ",\"serviceType\":\"IMAP\",\"email\":\"user@example.com\",\"active\":true}")
                    .collect(Collectors.joining(","));
            byte[] body = ("{\"offset\":" + offset
                    + ",\"totalSize\":2147483647,\"done\":false,\"records\":[" + records + "]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        source.start();
        try (var client = AurinkoService.createWithAppAuth(
                "http://127.0.0.1:" + source.getAddress().getPort(), "app-client", "app-secret")) {
            var service = new AurinkoAccountServiceImpl(client);

            var accounts = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> service.listAccounts("user-1"));

            assertThat(accounts).hasSize(200);
            assertThat(accounts.get(0).getId()).isEqualTo(1);
            assertThat(accounts.get(199).getId()).isEqualTo(200);
            assertThat(requests.get()).isEqualTo(4);
        } finally {
            source.stop(0);
        }
    }
}
