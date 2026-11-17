package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.aurinko.AurinkoLogin;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@RequiredArgsConstructor
public class AurinkoCallbackServlet extends HttpServlet {

    private final LoginStateManager stateManager;
    private final AurinkoAuthService authService;
    private final AuthorizationCompleter completer;
    private final ServerConfig config;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        LoginCookie.set(response, config, "", 0);
        LoginState state;
        try {
            state = stateManager.parseState(request.getParameter("state"));
        } catch (InvalidLoginStateException e) {
            badRequest(response, "Invalid login state");
            return;
        }
        var cookies = request.getCookies();
        String nonce = cookies == null ? null : Arrays.stream(cookies)
                .filter(cookie -> LoginCookie.NAME.equals(cookie.getName()))
                .map(cookie -> cookie.getValue()).findFirst().orElse(null);
        if (nonce == null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                state.getNonce().getBytes(StandardCharsets.UTF_8))) {
            badRequest(response, "Invalid login nonce");
            return;
        }
        var authorization = state.getRequest();
        String code = request.getParameter("code");
        if (!"success".equals(request.getParameter("status")) || code == null || code.isBlank()) {
            response.sendRedirect(OAuthRedirect.location(authorization.getRedirectUri(), "error", "access_denied",
                    authorization.getState()));
            return;
        }
        AurinkoLogin login;
        try {
            login = authService.exchangeCode(code);
        } catch (AurinkoException e) {
            response.sendRedirect(OAuthRedirect.location(authorization.getRedirectUri(), "error", "server_error",
                    authorization.getState()));
            return;
        }
        completer.complete(authorization, login.getUserId(), login.getUserSession(), response);
    }

    private void badRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(message);
    }
}
