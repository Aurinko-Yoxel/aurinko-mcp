package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@RequiredArgsConstructor
public class AuthorizeServlet extends HttpServlet {

    private final ClientRegistrationManager registrationManager;
    private final UserLogin userLogin;
    private final ServerConfig config;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        String clientId = request.getParameter("client_id");
        ClientRegistration registration;
        try {
            registration = registrationManager.find(clientId);
        } catch (InvalidClientException e) {
            badRequest(response, "Invalid client_id");
            return;
        }
        String redirectUri = request.getParameter("redirect_uri");
        if (redirectUri == null || !registration.getRedirectUris().contains(redirectUri)) {
            badRequest(response, "Invalid redirect_uri");
            return;
        }
        String state = request.getParameter("state");
        String codeChallenge = request.getParameter("code_challenge");
        String resource = request.getParameter("resource");
        if (!"code".equals(request.getParameter("response_type"))
                || codeChallenge == null || codeChallenge.isBlank()
                || !"S256".equals(request.getParameter("code_challenge_method"))
                || !config.getMcpUrl().equals(resource)) {
            response.sendRedirect(OAuthRedirect.location(redirectUri, "error", "invalid_request", state));
            return;
        }
        userLogin.start(new AuthorizationRequest(clientId, redirectUri, state, codeChallenge, resource), response);
    }

    private void badRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(message);
    }
}
