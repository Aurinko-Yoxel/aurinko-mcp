package com.yoxel.aurinko.mcp.support;

import com.yoxel.aurinko.mcp.accounts.ConnectCallbackServlet;
import com.yoxel.aurinko.mcp.accounts.ConnectPageRenderer;
import com.yoxel.aurinko.mcp.accounts.ConnectStartServlet;
import com.yoxel.aurinko.mcp.accounts.ConnectStateManager;
import com.yoxel.aurinko.mcp.accounts.ConnectStateManagerJweImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthorizeUrlBuilderImpl;
import com.yoxel.aurinko.mcp.aurinko.UserSignatureHelper;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

public class ConnectServletTestServer implements AutoCloseable {

    private final Server server;
    private final String baseUrl;
    private final ConnectStateManager stateManager;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    public ConnectServletTestServer(AurinkoAccountService accounts, AurinkoAuthService auth) throws Exception {
        this(accounts, auth, true);
    }

    public ConnectServletTestServer(AurinkoAccountService accounts, AurinkoAuthService auth, boolean secure) throws Exception {
        var config = new ServerConfig("127.0.0.1", 0,
                (secure ? "https" : "http") + "://mcp.example.com", Base64.getEncoder().encodeToString(new byte[32]));
        var aurinkoConfig = new AurinkoConfig("https://api.example.com", "app-client", "_-7dzLuqmYh3ZlVEMyIRAA",
                List.of("Google"), null);
        var clock = Clock.fixed(Instant.ofEpochSecond(1800000000), ZoneOffset.UTC);
        stateManager = new ConnectStateManagerJweImpl(new JweHelper(config.getTokenKey(), clock));
        var builder = new AurinkoAuthorizeUrlBuilderImpl(aurinkoConfig, config, new UserSignatureHelper(aurinkoConfig), clock);
        var renderer = new ConnectPageRenderer();
        server = new Server();
        var connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        var context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new ConnectStartServlet(stateManager, accounts, builder, renderer, config)),
                "/accounts/connect");
        context.addServlet(new ServletHolder(new ConnectCallbackServlet(stateManager, auth, renderer, config)),
                "/accounts/callback");
        server.setHandler(context);
        server.start();
        baseUrl = "http://127.0.0.1:" + connector.getLocalPort();
    }

    public ConnectStateManager getStateManager() {
        return stateManager;
    }

    public HttpResponse<String> get(String path, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() throws Exception {
        server.stop();
    }
}
