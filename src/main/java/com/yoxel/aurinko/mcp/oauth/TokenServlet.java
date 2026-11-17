package com.yoxel.aurinko.mcp.oauth;

import io.modelcontextprotocol.json.McpJsonMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class TokenServlet extends HttpServlet {

    private final ClientRegistrationManager registrationManager;
    private final List<GrantHandler> grantHandlers;
    private final McpJsonMapper jsonMapper;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            try {
                registrationManager.find(request.getParameter("client_id"));
            } catch (InvalidClientException e) {
                throw new OAuthErrorException("invalid_client", "Invalid client_id");
            }
            String grantType = request.getParameter("grant_type");
            GrantHandler handler = grantHandlers.stream()
                    .filter(candidate -> candidate.getGrantType().equals(grantType))
                    .findFirst()
                    .orElseThrow(() -> new OAuthErrorException("unsupported_grant_type", "Unsupported grant_type"));
            Map<String, String> parameters = new HashMap<>();
            request.getParameterMap().forEach((name, values) -> parameters.put(name, values[0]));
            TokenResponse tokens = handler.handle(parameters);
            response.getWriter().write(jsonMapper.writeValueAsString(Map.of(
                    "access_token", tokens.getAccessToken(),
                    "refresh_token", tokens.getRefreshToken(),
                    "token_type", "Bearer",
                    "expires_in", tokens.getExpiresIn())));
        } catch (OAuthErrorException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(jsonMapper.writeValueAsString(Map.of(
                    "error", e.getError(), "error_description", e.getErrorDescription())));
        }
    }
}
