package com.yoxel.aurinko.mcp.accounts;

import com.yoxel.aurinko.mcp.aurinko.AurinkoAccount;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthorizeUrlBuilder;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.tools.ToolErrorLogger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class ConnectStartServlet extends HttpServlet {

    private final ConnectStateManager stateManager;
    private final AurinkoAccountService accountService;
    private final AurinkoAuthorizeUrlBuilder urlBuilder;
    private final ConnectPageRenderer renderer;
    private final ServerConfig config;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        ConnectState state;
        try {
            state = stateManager.parseState(request.getParameter("state"));
        } catch (InvalidConnectStateException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("Invalid connection state");
            return;
        }
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        try {
            var emails = accountService.listAccounts(state.getUserId()).stream().map(AurinkoAccount::getEmail)
                    .filter(Objects::nonNull).toList();
            String url = urlBuilder.buildConnectUrl(state.getServiceType(), state.getUserId(), request.getParameter("state"));
            ConnectCookie.set(response, config, state.getNonce(), (int) ConnectState.LIFETIME.toSeconds());
            response.getWriter().write(renderer.confirmPage(state.getServiceType(), emails, url));
        } catch (AurinkoException | IllegalArgumentException | IllegalStateException e) {
            ToolErrorLogger.log(log, "Failed to start account connection", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(renderer.resultPage(false));
        }
    }
}
