package com.yoxel.aurinko.mcp.support;

import com.sun.net.httpserver.HttpServer;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeAurinkoServer implements AutoCloseable {

    private final HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{\"userId\":\"aurinko-user\",\"userSession\":\"user-session\",\"accountId\":42}";
    private volatile String method;
    private volatile String path;
    private volatile String authorization;
    private final AtomicInteger requests = new AtomicInteger();

    public FakeAurinkoServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/auth/token/", exchange -> {
            requests.incrementAndGet();
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().getRawPath();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    public AurinkoConfig config() {
        return new AurinkoConfig(getUrl(), "app-client", "app-secret", List.of("Google", "Office365"), null);
    }

    public String getUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respond(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getAuthorization() {
        return authorization;
    }

    public int getRequests() {
        return requests.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
