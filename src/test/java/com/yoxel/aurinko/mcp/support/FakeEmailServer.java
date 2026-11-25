package com.yoxel.aurinko.mcp.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeEmailServer implements AutoCloseable {

    private final HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{\"records\":[]}";
    private volatile String nextBody;
    private volatile URI requestUri;
    private volatile String requestBody;
    private volatile String requestMethod;
    private volatile CountDownLatch responseRelease;
    private final List<URI> requestUris = new CopyOnWriteArrayList<>();
    private volatile Headers requestHeaders;
    private final AtomicInteger requests = new AtomicInteger();

    public FakeEmailServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/email/messages", exchange -> {
            requests.incrementAndGet();
            requestUri = exchange.getRequestURI();
            requestUris.add(requestUri);
            requestHeaders = new Headers();
            requestHeaders.putAll(exchange.getRequestHeaders());
            requestMethod = exchange.getRequestMethod();
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (responseRelease != null) {
                try {
                    responseRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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
    }

    public void respondPages(String first, String next) {
        respond(200, first);
        this.nextBody = next;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void blockResponsesUntil(CountDownLatch release) {
        this.responseRelease = release;
    }

    public List<URI> getRequestUris() {
        return List.copyOf(requestUris);
    }

    public URI getRequestUri() {
        return requestUri;
    }

    public String getHeader(String name) {
        return requestHeaders.getFirst(name);
    }

    public int getRequests() {
        return requests.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
