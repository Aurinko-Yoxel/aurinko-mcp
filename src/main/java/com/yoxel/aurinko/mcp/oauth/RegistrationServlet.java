package com.yoxel.aurinko.mcp.oauth;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RegistrationServlet extends HttpServlet {

    private static final int MAX_REQUEST_BODY_BYTES = 16 * 1024;
    private static final int MAX_REDIRECT_URIS = 10;
    private static final int MAX_REDIRECT_URI_LENGTH = 2000;

    private final ClientRegistrationManager registrationManager;
    private final RedirectUriValidator redirectUriValidator;
    private final McpJsonMapper jsonMapper;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        Map<String, Object> metadata;
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BODY_BYTES + 1);
            if (body.length > MAX_REQUEST_BODY_BYTES) {
                error(response, "invalid_client_metadata");
                return;
            }
            metadata = jsonMapper.readValue(body, new TypeRef<Map<String, Object>>() {});
        } catch (IOException | IllegalArgumentException e) {
            error(response, "invalid_client_metadata");
            return;
        }
        if (metadata == null || !(metadata.get("redirect_uris") instanceof List<?> values)
                || values.isEmpty() || values.size() > MAX_REDIRECT_URIS
                || values.stream().anyMatch(value -> !(value instanceof String uri)
                || uri.length() > MAX_REDIRECT_URI_LENGTH)) {
            error(response, "invalid_client_metadata");
            return;
        }
        List<String> redirectUris = values.stream().map(String.class::cast).toList();
        if (redirectUris.stream().anyMatch(uri -> !redirectUriValidator.isValid(uri))) {
            error(response, "invalid_redirect_uri");
            return;
        }
        String clientId = registrationManager.register(redirectUris);
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.getWriter().write(jsonMapper.writeValueAsString(Map.of(
                "client_id", clientId,
                "redirect_uris", redirectUris,
                "token_endpoint_auth_method", "none")));
    }

    private void error(HttpServletResponse response, String error) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(jsonMapper.writeValueAsString(Map.of("error", error)));
    }
}
