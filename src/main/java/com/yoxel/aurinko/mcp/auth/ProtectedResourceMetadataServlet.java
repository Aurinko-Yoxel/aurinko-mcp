package com.yoxel.aurinko.mcp.auth;

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
public class ProtectedResourceMetadataServlet extends HttpServlet {

    private final ServerConfig config;
    private final McpJsonMapper jsonMapper;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(Map.of(
                "resource", config.getMcpUrl(),
                "authorization_servers", List.of(config.getPublicUrl()))));
    }
}
