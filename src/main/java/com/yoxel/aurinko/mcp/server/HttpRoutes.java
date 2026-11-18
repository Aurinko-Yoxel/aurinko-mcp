package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.mcp.accounts.ConnectCallbackServlet;
import com.yoxel.aurinko.mcp.accounts.ConnectPageRenderer;
import com.yoxel.aurinko.mcp.accounts.ConnectStartServlet;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.ProtectedResourceMetadataServlet;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.oauth.AurinkoCallbackServlet;
import com.yoxel.aurinko.mcp.oauth.AuthorizationServerMetadataServlet;
import com.yoxel.aurinko.mcp.oauth.AuthorizeServlet;
import com.yoxel.aurinko.mcp.oauth.RegistrationServlet;
import com.yoxel.aurinko.mcp.oauth.TokenServlet;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;

import java.util.EnumSet;

public class HttpRoutes {

    public static final String CONTEXT_PATH = "/";
    public static final String MCP_PATH = "/mcp";
    public static final String PROTECTED_RESOURCE_PATH = "/.well-known/oauth-protected-resource";
    public static final String PROTECTED_RESOURCE_MCP_PATH = PROTECTED_RESOURCE_PATH + MCP_PATH;
    public static final String AUTHORIZATION_SERVER_PATH = "/.well-known/oauth-authorization-server";
    public static final String REGISTRATION_PATH = "/oauth/register";
    public static final String AUTHORIZE_PATH = "/oauth/authorize";
    public static final String TOKEN_PATH = "/oauth/token";
    public static final String AURINKO_CALLBACK_PATH = "/oauth/aurinko/callback";
    public static final String CONNECT_PATH = "/accounts/connect";
    public static final String CONNECT_CALLBACK_PATH = "/accounts/callback";

    private HttpRoutes() {
    }

    public static void register(ServletContextHandler context, HttpServletStreamableServerTransportProvider transport,
            AurinkoComponents aurinko, OAuthComponents oauth, ServerConfig config,
            AurinkoConfig aurinkoConfig, McpJsonMapper jsonMapper) {
        ServletHolder servlet = new ServletHolder(transport);
        servlet.setAsyncSupported(true);
        context.addServlet(servlet, MCP_PATH);
        FilterHolder authFilter = new FilterHolder(new BearerAuthFilter(oauth.getAccessTokenVerifier(), config));
        authFilter.setAsyncSupported(true);
        context.addFilter(authFilter, MCP_PATH, EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));

        ServletHolder metadata = new ServletHolder(new ProtectedResourceMetadataServlet(config, jsonMapper));
        context.getServletHandler().addServletWithMappings(metadata, PROTECTED_RESOURCE_PATH, PROTECTED_RESOURCE_MCP_PATH);
        context.addServlet(new ServletHolder(new AuthorizationServerMetadataServlet(config, jsonMapper)), AUTHORIZATION_SERVER_PATH);
        context.addServlet(new ServletHolder(new RegistrationServlet(oauth.getClientRegistrationManager(),
                oauth.getRedirectUriValidator(), jsonMapper)), REGISTRATION_PATH);
        context.addServlet(new ServletHolder(new AuthorizeServlet(oauth.getClientRegistrationManager(),
                oauth.getUserLogin(), config)), AUTHORIZE_PATH);
        context.addServlet(new ServletHolder(new TokenServlet(oauth.getClientRegistrationManager(),
                oauth.getGrantHandlers(), jsonMapper)), TOKEN_PATH);
        context.addServlet(new ServletHolder(new AurinkoCallbackServlet(oauth.getLoginStateManager(),
                aurinko.getAuthService(), oauth.getAuthorizationCompleter(), config)), AURINKO_CALLBACK_PATH);
        var connectRenderer = new ConnectPageRenderer();
        context.addServlet(new ServletHolder(new ConnectStartServlet(oauth.getConnectStateManager(), aurinko.getAccountService(),
                oauth.getAuthorizeUrlBuilder(), connectRenderer, config)), CONNECT_PATH);
        context.addServlet(new ServletHolder(new ConnectCallbackServlet(oauth.getConnectStateManager(), aurinko.getAuthService(),
                connectRenderer, config)), CONNECT_CALLBACK_PATH);
    }
}
