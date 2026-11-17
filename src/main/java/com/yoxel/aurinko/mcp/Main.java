package com.yoxel.aurinko.mcp;

import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.mcp.accounts.ConnectCallbackServlet;
import com.yoxel.aurinko.mcp.accounts.ConnectPageRenderer;
import com.yoxel.aurinko.mcp.accounts.ConnectStartServlet;
import com.yoxel.aurinko.mcp.accounts.ConnectStateManagerJweImpl;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountServiceImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthServiceImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthorizeUrlBuilderImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarServiceImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoClientFactoryImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailServiceImpl;
import com.yoxel.aurinko.mcp.aurinko.UserSignatureHelper;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import com.yoxel.aurinko.mcp.auth.ProtectedResourceMetadataServlet;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.oauth.AurinkoCallbackServlet;
import com.yoxel.aurinko.mcp.oauth.AurinkoUserLogin;
import com.yoxel.aurinko.mcp.oauth.AuthorizationCodeGrantHandler;
import com.yoxel.aurinko.mcp.oauth.AuthorizationCodeManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.AuthorizationCompleterImpl;
import com.yoxel.aurinko.mcp.oauth.AuthorizationServerMetadataServlet;
import com.yoxel.aurinko.mcp.oauth.AuthorizeServlet;
import com.yoxel.aurinko.mcp.oauth.ClientRegistrationManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.DefaultRedirectUriValidator;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import com.yoxel.aurinko.mcp.oauth.LoginPageRendererImpl;
import com.yoxel.aurinko.mcp.oauth.LoginStateManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.PkceVerifierS256Impl;
import com.yoxel.aurinko.mcp.oauth.RefreshTokenGrantHandler;
import com.yoxel.aurinko.mcp.oauth.RegistrationServlet;
import com.yoxel.aurinko.mcp.oauth.TokenManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.TokenServlet;
import com.yoxel.aurinko.mcp.oauth.UsedCodesMemoryImpl;
import com.yoxel.aurinko.mcp.tools.ConnectAccountTool;
import com.yoxel.aurinko.mcp.tools.CreateEventTool;
import com.yoxel.aurinko.mcp.tools.GetMessageTool;
import com.yoxel.aurinko.mcp.tools.ListAccountsTool;
import com.yoxel.aurinko.mcp.tools.ListEventsTool;
import com.yoxel.aurinko.mcp.tools.McpTool;
import com.yoxel.aurinko.mcp.tools.PingTool;
import com.yoxel.aurinko.mcp.tools.SearchMessagesTool;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.LifeCycle;

import java.io.IOException;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromEnvironment();
        AurinkoConfig aurinkoConfig = AurinkoConfig.fromEnvironment();
        Server server = createServer(config, aurinkoConfig);
        server.setStopAtShutdown(true);
        try {
            server.start();
            log.info("Aurinko MCP server listening on {}:{} at /mcp", config.getHost(), config.getPort());
            server.join();
        } finally {
            server.stop();
        }
    }

    static Server createServer(ServerConfig config, AurinkoConfig aurinkoConfig) {
        var aurinkoService = AurinkoService.createWithAppAuth(aurinkoConfig.getUrl(),
                aurinkoConfig.getClientId(), aurinkoConfig.getClientSecret());
        return createServer(config, aurinkoConfig, aurinkoService, new AurinkoAccountServiceImpl(aurinkoService));
    }

    static Server createServer(ServerConfig config, AurinkoConfig aurinkoConfig,
            AurinkoService aurinkoService, AurinkoAccountService accountService) {
        var clock = Clock.systemUTC();
        var jweHelper = new JweHelper(config.getTokenKey(), clock);
        var tokenManager = new TokenManagerJweImpl(jweHelper, config.getMcpUrl());
        var registrationManager = new ClientRegistrationManagerJweImpl(jweHelper);
        var redirectUriValidator = new DefaultRedirectUriValidator();
        var codeManager = new AuthorizationCodeManagerJweImpl(jweHelper);
        var usedCodes = new UsedCodesMemoryImpl(clock);
        var pkceVerifier = new PkceVerifierS256Impl();
        var codeGrantHandler = new AuthorizationCodeGrantHandler(codeManager, tokenManager, pkceVerifier, usedCodes);
        var refreshGrantHandler = new RefreshTokenGrantHandler(tokenManager);
        var completer = new AuthorizationCompleterImpl(codeManager);
        var stateManager = new LoginStateManagerJweImpl(jweHelper);
        var signatureHelper = new UserSignatureHelper(aurinkoConfig);
        var urlBuilder = new AurinkoAuthorizeUrlBuilderImpl(aurinkoConfig, config, signatureHelper, clock);
        var connectStateManager = new ConnectStateManagerJweImpl(jweHelper);
        var connectRenderer = new ConnectPageRenderer();
        var renderer = new LoginPageRendererImpl();
        var userLogin = new AurinkoUserLogin(stateManager, urlBuilder, renderer, config, aurinkoConfig);
        var jsonMapper = McpJsonDefaults.getMapper();
        var transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                .contextExtractor(McpPrincipalHelper::toContext)
                .build();
        String version = Main.class.getPackage().getImplementationVersion();
        var accessValidator = new AccountAccessValidator(accountService);
        var clientFactory = new AurinkoClientFactoryImpl(aurinkoConfig);
        var emailService = new AurinkoEmailServiceImpl(clientFactory);
        var calendarService = new AurinkoCalendarServiceImpl(clientFactory);
        List<McpTool> tools = List.of(new PingTool(), new ListAccountsTool(accountService, jsonMapper),
                new SearchMessagesTool(accessValidator, emailService, jsonMapper),
                new GetMessageTool(accessValidator, emailService, jsonMapper),
                new ListEventsTool(accessValidator, calendarService, jsonMapper),
                new CreateEventTool(accessValidator, calendarService, jsonMapper),
                new ConnectAccountTool(connectStateManager, config, jsonMapper));
        var mcpServerBuilder = McpServer.sync(transport)
                .serverInfo("aurinko-mcp", version != null ? version : "development")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build());
        for (McpTool tool : tools) {
            mcpServerBuilder.tools(tool.specification());
        }
        var mcpServer = mcpServerBuilder.build();

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(config.getHost());
        connector.setPort(config.getPort());
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        ServletHolder servlet = new ServletHolder(transport);
        servlet.setAsyncSupported(true);
        context.addServlet(servlet, "/mcp");
        FilterHolder authFilter = new FilterHolder(new BearerAuthFilter(tokenManager, config));
        authFilter.setAsyncSupported(true);
        context.addFilter(authFilter, "/mcp", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));

        ServletHolder metadata = new ServletHolder(new ProtectedResourceMetadataServlet(config, jsonMapper));
        context.getServletHandler().addServletWithMappings(metadata,
                "/.well-known/oauth-protected-resource",
                "/.well-known/oauth-protected-resource/mcp");
        context.addServlet(new ServletHolder(new AuthorizationServerMetadataServlet(config, jsonMapper)),
                "/.well-known/oauth-authorization-server");
        context.addServlet(new ServletHolder(new RegistrationServlet(
                registrationManager, redirectUriValidator, jsonMapper)), "/oauth/register");
        context.addServlet(new ServletHolder(new AuthorizeServlet(registrationManager, userLogin, config)),
                "/oauth/authorize");
        context.addServlet(new ServletHolder(new TokenServlet(registrationManager,
                List.of(codeGrantHandler, refreshGrantHandler), jsonMapper)), "/oauth/token");
        var authService = new AurinkoAuthServiceImpl(aurinkoService);
        context.addServlet(new ServletHolder(new AurinkoCallbackServlet(stateManager, authService, completer, config)),
                "/oauth/aurinko/callback");
        context.addServlet(new ServletHolder(new ConnectStartServlet(connectStateManager, accountService,
                urlBuilder, connectRenderer, config)), "/accounts/connect");
        context.addServlet(new ServletHolder(new ConnectCallbackServlet(connectStateManager, authService,
                connectRenderer, config)), "/accounts/callback");
        server.setHandler(context);
        server.addEventListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopping(LifeCycle event) {
                try {
                    mcpServer.closeGracefully();
                } finally {
                    try {
                        aurinkoService.close();
                    } catch (IOException e) {
                        log.warn("Unable to close Aurinko client");
                    }
                }
            }
        });
        return server;
    }
}
