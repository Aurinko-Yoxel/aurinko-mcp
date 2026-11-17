package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import io.modelcontextprotocol.json.McpJsonMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class AuthorizationServerMetadataServlet extends HttpServlet {

    private final ServerConfig config;
    private final McpJsonMapper jsonMapper;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String baseUrl = config.getPublicUrl();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(Map.of(
                "issuer", baseUrl,
                "authorization_endpoint", baseUrl + "/oauth/authorize",
                "token_endpoint", baseUrl + "/oauth/token",
                "registration_endpoint", baseUrl + "/oauth/register",
                "response_types_supported", List.of("code"),
                "grant_types_supported", List.of("authorization_code", "refresh_token"),
                "code_challenge_methods_supported", List.of("S256"),
                "token_endpoint_auth_methods_supported", List.of("none"))));
    }
}
