package com.yoxel.aurinko.mcp.support;

import com.sun.net.httpserver.HttpServer;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeCalendarServer implements AutoCloseable {

    private final HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{\"records\":[]}";
    private volatile String nextBody;
    private volatile URI requestUri;
    private final List<URI> requestUris = new CopyOnWriteArrayList<>();
    private volatile String requestBody;
    private volatile String requestMethod;
    private final AtomicInteger requests = new AtomicInteger();

    public FakeCalendarServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/calendars/", exchange -> {
            requests.incrementAndGet();
            requestUri = exchange.getRequestURI();
            requestUris.add(requestUri);
            requestMethod = exchange.getRequestMethod();
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = nextBody != null && requestUri.getRawQuery() != null
                    && requestUri.getRawQuery().contains("pageToken=") ? nextBody : body;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    public AurinkoConfig config() {
        return new AurinkoConfig("http://127.0.0.1:" + server.getAddress().getPort(),
                "unused-app-id", "unused-app-secret", List.of("Google"), null);
    }

    public void respond(int status, String body) {
        this.status = status;
        this.body = body;
        this.nextBody = null;
    }

    public void respondPages(String first, String next) {
        respond(200, first);
        this.nextBody = next;
    }

    public List<URI> getRequestUris() {
        return List.copyOf(requestUris);
    }

    public URI getRequestUri() {
        return requestUri;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public int getRequests() {
        return requests.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
