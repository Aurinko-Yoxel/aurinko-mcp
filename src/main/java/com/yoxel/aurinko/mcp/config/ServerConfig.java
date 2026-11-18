package com.yoxel.aurinko.mcp.config;

import lombok.ToString;
import lombok.Value;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

@Value
public class ServerConfig {

    String host;
    int port;
    String publicUrl;
    Path toolsConfigFile;
    @ToString.Exclude
    SecretKey tokenKey;

    public ServerConfig(String host, int port, String publicUrl, String tokenKey) {
        this(host, port, publicUrl, tokenKey, null);
    }

    public ServerConfig(String host, int port, String publicUrl, String tokenKey, Path toolsConfigFile) {
        this.toolsConfigFile = toolsConfigFile;
        this.host = host;
        this.port = port;
        this.publicUrl = normalizePublicUrl(publicUrl);
        if (tokenKey == null || tokenKey.isBlank()) {
            throw new IllegalArgumentException("TOKEN_KEY is required and must be base64-encoded 32 bytes");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(tokenKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("TOKEN_KEY must be valid base64 encoding of 32 bytes");
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("TOKEN_KEY must decode to exactly 32 bytes");
        }
        this.tokenKey = new SecretKeySpec(decoded, "AES");
    }

    public String getMcpUrl() {
        return publicUrl + "/mcp";
    }

    private static String normalizePublicUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PUBLIC_URL must be an absolute HTTP or HTTPS URL");
        }
        String normalized = value.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("PUBLIC_URL must be an absolute HTTP or HTTPS URL");
        }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("PUBLIC_URL must be an absolute HTTP or HTTPS URL without credentials, query or fragment");
        }
        if ("http".equals(uri.getScheme()) && !LocalHostHelper.isLocal(uri)) {
            throw new IllegalArgumentException("PUBLIC_URL must use HTTPS unless the host is local");
        }
        return normalized;
    }

    public static ServerConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static ServerConfig fromEnvironment(Map<String, String> environment) {
        String host = environment.getOrDefault("HOST", "127.0.0.1").trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("HOST must not be blank");
        }

        int port;
        try {
            port = Integer.parseInt(environment.getOrDefault("PORT", "8080").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be an integer between 1 and 65535");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("PORT must be an integer between 1 and 65535");
        }

        String toolsFile = environment.get("TOOLS_CONFIG_FILE");
        Path toolsConfigFile = toolsFile == null || toolsFile.isBlank() ? null : Path.of(toolsFile.trim());
        return new ServerConfig(host, port,
                environment.getOrDefault("PUBLIC_URL", "http://127.0.0.1:" + port),
                environment.get("TOKEN_KEY"), toolsConfigFile);
    }
}
