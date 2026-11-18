package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.mcp.accounts.ConnectStateManager;
import com.yoxel.aurinko.mcp.accounts.ConnectStateManagerJweImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthorizeUrlBuilder;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthorizeUrlBuilderImpl;
import com.yoxel.aurinko.mcp.aurinko.UserSignatureHelper;
import com.yoxel.aurinko.mcp.auth.AccessTokenVerifier;
import com.yoxel.aurinko.mcp.auth.McpSessionOwners;
import com.yoxel.aurinko.mcp.auth.McpSessionOwnersMemoryImpl;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.oauth.AurinkoUserLogin;
import com.yoxel.aurinko.mcp.oauth.AuthorizationCodeGrantHandler;
import com.yoxel.aurinko.mcp.oauth.AuthorizationCodeManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.AuthorizationCompleter;
import com.yoxel.aurinko.mcp.oauth.AuthorizationCompleterImpl;
import com.yoxel.aurinko.mcp.oauth.ClientRegistrationManager;
import com.yoxel.aurinko.mcp.oauth.ClientRegistrationManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.DefaultRedirectUriValidator;
import com.yoxel.aurinko.mcp.oauth.GrantHandler;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import com.yoxel.aurinko.mcp.oauth.LoginPageRendererImpl;
import com.yoxel.aurinko.mcp.oauth.LoginStateManager;
import com.yoxel.aurinko.mcp.oauth.LoginStateManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.PkceVerifierS256Impl;
import com.yoxel.aurinko.mcp.oauth.RedirectUriValidator;
import com.yoxel.aurinko.mcp.oauth.RefreshTokenGrantHandler;
import com.yoxel.aurinko.mcp.oauth.TokenManager;
import com.yoxel.aurinko.mcp.oauth.TokenManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.UsedCodesMemoryImpl;
import com.yoxel.aurinko.mcp.oauth.UserLogin;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.Clock;
import java.util.List;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OAuthComponents {

    JweHelper jweHelper;
    TokenManager tokenManager;
    AccessTokenVerifier accessTokenVerifier;
    McpSessionOwners sessionOwners;
    ClientRegistrationManager clientRegistrationManager;
    RedirectUriValidator redirectUriValidator;
    List<GrantHandler> grantHandlers;
    AuthorizationCompleter authorizationCompleter;
    LoginStateManager loginStateManager;
    ConnectStateManager connectStateManager;
    AurinkoAuthorizeUrlBuilder authorizeUrlBuilder;
    UserLogin userLogin;

    public static OAuthComponents create(ServerConfig config, AurinkoConfig aurinkoConfig, Clock clock) {
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
        var loginStateManager = new LoginStateManagerJweImpl(jweHelper);
        var signatureHelper = new UserSignatureHelper(aurinkoConfig);
        var urlBuilder = new AurinkoAuthorizeUrlBuilderImpl(aurinkoConfig, config, signatureHelper, clock);
        var connectStateManager = new ConnectStateManagerJweImpl(jweHelper);
        var renderer = new LoginPageRendererImpl();
        var userLogin = new AurinkoUserLogin(loginStateManager, urlBuilder, renderer, config, aurinkoConfig);
        var sessionOwners = new McpSessionOwnersMemoryImpl(clock);
        return new OAuthComponents(jweHelper, tokenManager, tokenManager, sessionOwners, registrationManager, redirectUriValidator,
                List.of(codeGrantHandler, refreshGrantHandler), completer, loginStateManager, connectStateManager,
                urlBuilder, userLogin);
    }
}
