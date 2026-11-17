package com.yoxel.aurinko.mcp.config;

import lombok.ToString;
import lombok.Value;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Value
public class AurinkoConfig {

    String url;
    String clientId;
    @ToString.Exclude
    String clientSecret;
    List<String> loginServiceTypes;
    String loginScopes;

    public AurinkoConfig(String url, String clientId, String clientSecret,
            List<String> loginServiceTypes, String loginScopes) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("AURINKO_CLIENT_ID is required");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("AURINKO_CLIENT_SECRET is required");
        }
        String normalized = url == null ? "" : url.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("AURINKO_URL must be an absolute HTTP or HTTPS URL");
        }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("AURINKO_URL must be an absolute HTTP or HTTPS URL");
        }
        if (loginServiceTypes == null || loginServiceTypes.isEmpty()
                || loginServiceTypes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("AURINKO_LOGIN_SERVICE_TYPES must contain at least one service type");
        }
        this.url = normalized;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.loginServiceTypes = loginServiceTypes.stream().map(String::trim).distinct().toList();
        this.loginScopes = loginScopes == null || loginScopes.isBlank() ? null : loginScopes.trim();
    }

    public static AurinkoConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static AurinkoConfig fromEnvironment(Map<String, String> environment) {
        return new AurinkoConfig(environment.getOrDefault("AURINKO_URL", "https://api.aurinko.io"),
                environment.get("AURINKO_CLIENT_ID"), environment.get("AURINKO_CLIENT_SECRET"),
                Arrays.asList(environment.getOrDefault("AURINKO_LOGIN_SERVICE_TYPES", "Google,Office365").split(",", -1)),
                environment.get("AURINKO_LOGIN_SCOPES"));
    }
}
