package com.yoxel.aurinko.mcp.config;

import lombok.ToString;
import lombok.Value;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Value
public class AurinkoConfig {

    private static final List<String> DEFAULT_ACCOUNT_SERVICE_TYPES = List.of("Google", "Office365", "iCloud", "IMAP", "CalDav");
    private static final String DEFAULT_LOGIN_SCOPES = "Mail.Read Calendar.ReadWrite";
    private static final String DEFAULT_IMAP_SCOPES = "Mail.Read";
    private static final String DEFAULT_CALDAV_SCOPES = "Calendar.ReadWrite";

    String url;
    String clientId;
    @ToString.Exclude
    String clientSecret;
    List<String> loginServiceTypes;
    List<String> accountServiceTypes;
    String loginScopes;
    String imapScopes;
    String caldavScopes;

    public AurinkoConfig(String url, String clientId, String clientSecret,
            List<String> loginServiceTypes, String loginScopes) {
        this(url, clientId, clientSecret, loginServiceTypes, loginScopes, DEFAULT_IMAP_SCOPES, DEFAULT_CALDAV_SCOPES);
    }

    public AurinkoConfig(String url, String clientId, String clientSecret,
            List<String> loginServiceTypes, String loginScopes, String imapScopes, String caldavScopes) {
        this(url, clientId, clientSecret, loginServiceTypes, loginScopes, imapScopes, caldavScopes,
                DEFAULT_ACCOUNT_SERVICE_TYPES);
    }

    public AurinkoConfig(String url, String clientId, String clientSecret,
            List<String> loginServiceTypes, String loginScopes, String imapScopes, String caldavScopes,
            List<String> accountServiceTypes) {
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
        if ("http".equals(uri.getScheme()) && !LocalHostHelper.isLocal(uri)) {
            throw new IllegalArgumentException("AURINKO_URL must use HTTPS unless the host is local");
        }
        if (loginServiceTypes == null || loginServiceTypes.isEmpty()
                || loginServiceTypes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("AURINKO_LOGIN_SERVICE_TYPES must contain at least one service type");
        }
        if (accountServiceTypes == null || accountServiceTypes.isEmpty()
                || accountServiceTypes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("AURINKO_ACCOUNT_SERVICE_TYPES must contain at least one service type");
        }
        this.accountServiceTypes = accountServiceTypes.stream().map(String::trim).distinct().toList();
        this.url = normalized;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.loginServiceTypes = loginServiceTypes.stream().map(String::trim).distinct().toList();
        this.loginScopes = loginScopes == null || loginScopes.isBlank() ? DEFAULT_LOGIN_SCOPES : loginScopes.trim();
        this.imapScopes = imapScopes;
        this.caldavScopes = caldavScopes;
    }

    public static AurinkoConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static AurinkoConfig fromEnvironment(Map<String, String> environment) {
        return new AurinkoConfig(environment.getOrDefault("AURINKO_URL", "https://api.aurinko.io"),
                environment.get("AURINKO_CLIENT_ID"), environment.get("AURINKO_CLIENT_SECRET"),
                Arrays.asList(environment.getOrDefault("AURINKO_LOGIN_SERVICE_TYPES", "Google,Office365").split(",", -1)),
                environment.getOrDefault("AURINKO_LOGIN_SCOPES", DEFAULT_LOGIN_SCOPES),
                environment.getOrDefault("AURINKO_IMAP_SCOPES", DEFAULT_IMAP_SCOPES),
                environment.getOrDefault("AURINKO_CALDAV_SCOPES", DEFAULT_CALDAV_SCOPES),
                Arrays.asList(environment.getOrDefault("AURINKO_ACCOUNT_SERVICE_TYPES",
                        String.join(",", DEFAULT_ACCOUNT_SERVICE_TYPES)).split(",", -1)));
    }
}
