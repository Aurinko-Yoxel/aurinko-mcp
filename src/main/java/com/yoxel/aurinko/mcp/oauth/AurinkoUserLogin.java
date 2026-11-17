package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthorizeUrlBuilder;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class AurinkoUserLogin implements UserLogin {

    private final LoginStateManager stateManager;
    private final AurinkoAuthorizeUrlBuilder urlBuilder;
    private final LoginPageRenderer renderer;
    private final ServerConfig config;
    private final AurinkoConfig aurinkoConfig;

    @Override
    public void start(AuthorizationRequest request, HttpServletResponse response) throws IOException {
        String nonce = UUID.randomUUID().toString();
        String state = stateManager.createState(new LoginState(request, nonce));
        Map<String, String> authorizeUrls = new LinkedHashMap<>();
        for (String serviceType : aurinkoConfig.getLoginServiceTypes()) {
            authorizeUrls.put(serviceType, urlBuilder.build(serviceType,
                    config.getPublicUrl() + "/oauth/aurinko/callback", state));
        }
        LoginCookie.set(response, config, nonce, (int) LoginState.LIFETIME.toSeconds());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(renderer.render(request, authorizeUrls));
    }
}
