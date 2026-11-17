package com.yoxel.aurinko.mcp.auth;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.regex.Pattern;

public class BearerAuthFilter implements Filter {

    public static final String PRINCIPAL_ATTRIBUTE = McpPrincipal.class.getName();

    private static final Pattern BEARER = Pattern.compile("Bearer +([^\\s]+)", Pattern.CASE_INSENSITIVE);

    private final AccessTokenVerifier verifier;
    private final String challenge;

    public BearerAuthFilter(AccessTokenVerifier verifier, ServerConfig config) {
        this.verifier = verifier;
        this.challenge = "Bearer resource_metadata=\"" + config.getPublicUrl()
                + "/.well-known/oauth-protected-resource\"";
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;
        String authorization = httpRequest.getHeader("Authorization");
        if (authorization == null) {
            unauthorized(httpResponse);
            return;
        }

        var matcher = BEARER.matcher(authorization);
        if (!matcher.matches()) {
            unauthorized(httpResponse);
            return;
        }

        McpPrincipal principal;
        try {
            principal = verifier.verify(matcher.group(1));
        } catch (InvalidTokenException e) {
            unauthorized(httpResponse);
            return;
        }
        httpRequest.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", challenge);
    }
}
