package com.yoxel.aurinko.mcp.accounts;

import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.tools.ToolErrorLogger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
public class ConnectCallbackServlet extends HttpServlet {

    private final ConnectStateManager stateManager;
    private final AurinkoAuthService authService;
    private final ConnectPageRenderer renderer;
    private final ServerConfig config;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        ConnectCookie.set(response, config, "", 0);
        ConnectState state;
        try {
            state = stateManager.parseState(request.getParameter("state"));
        } catch (InvalidConnectStateException e) {
            badRequest(response, "Invalid connection state");
            return;
        }
        var cookies = request.getCookies();
        String nonce = cookies == null ? null : Arrays.stream(cookies)
                .filter(cookie -> ConnectCookie.NAME.equals(cookie.getName()))
                .map(cookie -> cookie.getValue()).findFirst().orElse(null);
        if (nonce == null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                state.getNonce().getBytes(StandardCharsets.UTF_8))) {
            badRequest(response, "Invalid connection nonce");
            return;
        }
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        String code = request.getParameter("code");
        if (!"success".equals(request.getParameter("status")) || code == null || code.isBlank()) {
            response.getWriter().write(renderer.resultPage(false));
            return;
        }
        boolean success = false;
        try {
            var login = authService.exchangeCode(code);
            success = state.getUserId().equals(login.getUserId());
            if (!success) {
                log.error("Aurinko account connection returned an unexpected user");
            }
        } catch (AurinkoException e) {
            ToolErrorLogger.log(log, "Failed to complete account connection", e);
        }
        response.getWriter().write(renderer.resultPage(success));
    }

    private void badRequest(HttpServletResponse response, String text) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(text);
    }
}
