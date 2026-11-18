package com.yoxel.aurinko.mcp;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.server.AurinkoComponents;
import com.yoxel.aurinko.mcp.server.JettyServerFactory;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import com.yoxel.aurinko.mcp.support.FakeAurinkoServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OAuthFlowTest {

    private ServerConfig config = new ServerConfig("127.0.0.1", 0,
            "https://mcp.example.com/", Base64.getEncoder().encodeToString(new byte[32]));
    private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String redirectUri = "https://example.com/cb?source=chat%20client";
    private final String state = "state + &=/? юзер";
    private final String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private final String codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private Server server;
    private FakeAurinkoServer aurinko;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        aurinko = new FakeAurinkoServer();
        var aurinkoConfig = aurinko.config();
        server = JettyServerFactory.create(config, aurinkoConfig, AurinkoComponents.create(aurinkoConfig));
        server.start();
        baseUrl = "http://127.0.0.1:" + ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        try {
            if (server != null) {
                server.stop();
            }
        } finally {
            if (aurinko != null) {
                aurinko.close();
            }
        }
    }

    @Test
    void authorizationServerMetadataAvailable() throws Exception {
        var response = get("/.well-known/oauth-authorization-server");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response)).isEqualTo(Map.of(
                "issuer", config.getPublicUrl(),
                "authorization_endpoint", config.getPublicUrl() + "/oauth/authorize",
                "token_endpoint", config.getPublicUrl() + "/oauth/token",
                "registration_endpoint", config.getPublicUrl() + "/oauth/register",
                "response_types_supported", List.of("code"),
                "grant_types_supported", List.of("authorization_code", "refresh_token"),
                "code_challenge_methods_supported", List.of("S256"),
                "token_endpoint_auth_methods_supported", List.of("none")));
    }

    @Test
    void registeredClientReceivesAuthorizationCodeAndState() throws Exception {
        String clientId = register();
        var login = beginLogin(parameters(clientId));
        var response = callback(login.state(), login.cookie(), "success", "provider-code");

        assertThat(response.statusCode()).isEqualTo(302);
        URI location = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(location.getScheme()).isEqualTo("https");
        assertThat(location.getHost()).isEqualTo("example.com");
        assertThat(location.getPath()).isEqualTo("/cb");
        var query = query(location);
        assertThat(query).containsEntry("state", state).containsEntry("source", "chat client");
        assertThat(query.get("code")).isNotBlank();
        assertThat(query).doesNotContainKey("error");
        var helper = new JweHelper(config.getTokenKey(), Clock.systemUTC());
        assertThat(helper.decrypt(query.get("code"), "code")).isEqualTo(Map.of(
                "userId", "aurinko-user",
                "userSession", "user-session",
                "clientId", clientId,
                "redirectUri", redirectUri,
                "codeChallenge", "challenge-1",
                "resource", config.getMcpUrl()));
    }

    @Test
    void invalidRegistrationRedirectUriRejected() throws Exception {
        var response = post(jsonMapper.writeValueAsString(Map.of("redirect_uris", List.of("http://example.com/cb"))));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response)).containsEntry("error", "invalid_redirect_uri");
    }

    @Test
    void oversizedRegistrationBodyRejected() throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of(
                "redirect_uris", List.of(redirectUri), "client_name", "a".repeat(16 * 1024)));

        var response = post(body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response)).containsEntry("error", "invalid_client_metadata");
    }

    @Test
    void tooManyRegistrationRedirectUrisRejected() throws Exception {
        var uris = IntStream.range(0, 11).mapToObj(index -> "https://example.com/cb/" + index).toList();

        var response = post(jsonMapper.writeValueAsString(Map.of("redirect_uris", uris)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response)).containsEntry("error", "invalid_client_metadata");
    }

    @Test
    void oversizedRegistrationRedirectUriRejected() throws Exception {
        String uri = "https://example.com/";
        uri += "a".repeat(2001 - uri.length());

        var response = post(jsonMapper.writeValueAsString(Map.of("redirect_uris", List.of(uri))));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response)).containsEntry("error", "invalid_client_metadata");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "not-json", "{\"redirect_uris\":[]}",
            "{\"redirect_uris\":[1]}", "{\"redirect_uris\":\"https://example.com/cb\"}"})
    void malformedRegistrationMetadataRejected(String body) throws Exception {
        var response = post(body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response)).containsEntry("error", "invalid_client_metadata");
    }

    @Test
    void unknownClientRejectedWithoutRedirect() throws Exception {
        var response = authorize(parameters("unknown-client"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("client_id");
    }

    @Test
    void unregisteredRedirectUriRejectedWithoutRedirect() throws Exception {
        var parameters = parameters(register());
        parameters.put("redirect_uri", "https://other.example.com/cb");
        var response = authorize(parameters);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("redirect_uri");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingChallenge", "plain", "foreignResource", "token"})
    void invalidAuthorizationRequestRedirectedWithErrorAndState(String invalidParameter) throws Exception {
        var parameters = parameters(register());
        switch (invalidParameter) {
            case "missingChallenge" -> parameters.remove("code_challenge");
            case "plain" -> parameters.put("code_challenge_method", "plain");
            case "foreignResource" -> parameters.put("resource", "https://other.example.com/mcp");
            case "token" -> parameters.put("response_type", "token");
            default -> throw new IllegalArgumentException(invalidParameter);
        }
        var response = authorize(parameters);

        assertThat(response.statusCode()).isEqualTo(302);
        URI location = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(location.getHost()).isEqualTo("example.com");
        assertThat(location.getPath()).isEqualTo("/cb");
        assertThat(query(location)).containsEntry("error", "invalid_request")
                .containsEntry("state", state).containsEntry("source", "chat client")
                .doesNotContainKey("code");
    }

    @Test
    void authorizationCodeExchangedForTokensAndPing() throws Exception {
        String clientId = register();
        var response = exchangeCode(clientId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        var tokens = json(response);
        assertThat(tokens).containsEntry("token_type", "Bearer");
        assertThat(((Number) tokens.get("expires_in")).longValue()).isEqualTo(3600);
        assertThat(tokens.get("refresh_token")).isInstanceOf(String.class);
        assertThat(new JweHelper(config.getTokenKey(), Clock.systemUTC())
                .decrypt((String) tokens.get("access_token"), "access")).containsEntry("userId", "aurinko-user").containsEntry("userSession", "user-session");
        ping((String) tokens.get("access_token"));
    }

    @Test
    void refreshedAccessTokenWorksWithPing() throws Exception {
        String clientId = register();
        var original = json(exchangeCode(clientId));
        String oldRefresh = (String) original.get("refresh_token");

        var response = token(Map.of("grant_type", "refresh_token", "client_id", clientId,
                "refresh_token", oldRefresh));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        var refreshed = json(response);
        assertThat(refreshed.get("refresh_token")).isNotEqualTo(oldRefresh);
        assertThat(refreshed.get("access_token")).isNotEqualTo(original.get("access_token"));
        ping((String) refreshed.get("access_token"));
        var repeated = token(Map.of("grant_type", "refresh_token", "client_id", clientId,
                "refresh_token", oldRefresh));
        assertThat(repeated.statusCode()).isEqualTo(200);
    }

    @Test
    void unsupportedGrantTypeRejected() throws Exception {
        var response = token(Map.of("client_id", register(), "grant_type", "unknown"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(json(response)).containsEntry("error", "unsupported_grant_type");
        assertThat(json(response).get("error_description")).isInstanceOf(String.class);
    }

    @Test
    void invalidTokenRequestClientRejected() throws Exception {
        var response = token(Map.of("client_id", "foreign-client", "grant_type", "authorization_code"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(json(response)).containsEntry("error", "invalid_client");
        assertThat(json(response).get("error_description")).isInstanceOf(String.class);
    }

    @Test
    void providerErrorRedirectedAsAccessDenied() throws Exception {
        var login = beginLogin(parameters(register()));

        var response = callback(login.state(), login.cookie(), "error", "provider-code");

        assertCallbackError(response, "access_denied");
        assertThat(aurinko.getRequests()).isZero();
    }

    @Test
    void missingProviderCodeRedirectedAsAccessDenied() throws Exception {
        var login = beginLogin(parameters(register()));

        var response = callback(login.state(), login.cookie(), "success", "");

        assertCallbackError(response, "access_denied");
        assertThat(aurinko.getRequests()).isZero();
    }

    @Test
    void corruptedLoginStateRejected() throws Exception {
        var response = callback("corrupted", null, "success", "provider-code");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("Invalid login state");
        assertCookieDeleted(response);
        assertThat(aurinko.getRequests()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong"})
    void missingOrMismatchedNonceRejected(String cookieType) throws Exception {
        var login = beginLogin(parameters(register()));
        String cookie = "missing".equals(cookieType) ? null : "aurinko_login_nonce=wrong-nonce";

        var response = callback(login.state(), cookie, "success", "provider-code");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("Invalid login nonce");
        assertCookieDeleted(response);
        assertThat(aurinko.getRequests()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    void aurinkoExchangeErrorRedirectedAsServerError(int status) throws Exception {
        var login = beginLogin(parameters(register()));
        aurinko.respond(status, "{\"code\":\"failed\"}");

        var response = callback(login.state(), login.cookie(), "success", "provider-code");

        assertCallbackError(response, "server_error");
        assertThat(aurinko.getRequests()).isEqualTo(1);
    }

    @Test
    void singleProviderShowsPageAndHttpCookieIsNotSecure() throws Exception {
        server.stop();
        config = new ServerConfig("127.0.0.1", 0, "http://mcp.example.com",
                Base64.getEncoder().encodeToString(new byte[32]));
        var singleProvider = new AurinkoConfig(aurinko.getUrl(), "app-client", "app-secret", List.of("Google"), null);
        server = JettyServerFactory.create(config, singleProvider, AurinkoComponents.create(singleProvider));
        server.start();
        baseUrl = "http://127.0.0.1:" + ((ServerConnector) server.getConnectors()[0]).getLocalPort();

        var login = beginLogin(parameters(register()));

        assertThat(login.response().body()).contains("Sign in with Google").doesNotContain("Sign in with Microsoft");
        assertThat(login.response().headers().firstValue("Set-Cookie").orElseThrow()).doesNotContain("Secure");
    }

    private LoginAttempt beginLogin(Map<String, String> parameters) throws Exception {
        var response = authorize(parameters);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
        assertThat(response.body()).contains("example.com", "Sign in with Google");
        var matcher = Pattern.compile("href=\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        URI url = URI.create(matcher.group(1).replace("&amp;", "&"));
        assertThat(url.getPath()).isEqualTo("/v1/auth/authorizeUser");
        assertThat(query(url)).containsEntry("accountRole", "primary")
                .containsEntry("returnUrl", config.getPublicUrl() + "/oauth/aurinko/callback");
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).contains("HttpOnly", "SameSite=Lax", "Max-Age=600");
        if (config.getPublicUrl().startsWith("https:")) {
            assertThat(cookie).contains("Secure");
        }
        return new LoginAttempt(response, query(url).get("state"), cookie.split(";", 2)[0]);
    }

    private HttpResponse<String> callback(String loginState, String cookie, String status, String code) throws Exception {
        String query = Map.of("state", loginState, "status", status, "code", code).entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/oauth/aurinko/callback?" + query))
                .timeout(Duration.ofSeconds(10));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertCallbackError(HttpResponse<String> response, String error) {
        assertThat(response.statusCode()).isEqualTo(302);
        URI location = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(location.getHost()).isEqualTo("example.com");
        assertThat(query(location)).containsEntry("error", error).containsEntry("state", state);
        assertCookieDeleted(response);
    }

    private void assertCookieDeleted(HttpResponse<String> response) {
        var cookie = HttpCookie.parse(response.headers().firstValue("Set-Cookie").orElseThrow()).get(0);
        assertThat(cookie.getName()).isEqualTo("aurinko_login_nonce");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.hasExpired()).isTrue();
    }

    private record LoginAttempt(HttpResponse<String> response, String state, String cookie) {}

    private HttpResponse<String> exchangeCode(String clientId) throws Exception {
        var parameters = parameters(clientId);
        parameters.put("code_challenge", codeChallenge);
        var login = beginLogin(parameters);
        var authorization = callback(login.state(), login.cookie(), "success", "provider-code");
        assertThat(authorization.statusCode()).isEqualTo(302);
        assertCookieDeleted(authorization);
        URI location = URI.create(authorization.headers().firstValue("Location").orElseThrow());
        String code = query(location).get("code");
        return token(Map.of("grant_type", "authorization_code", "client_id", clientId,
                "code", code, "redirect_uri", redirectUri, "code_verifier", codeVerifier,
                "resource", config.getMcpUrl()));
    }

    private HttpResponse<String> token(Map<String, String> parameters) throws Exception {
        String body = parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/oauth/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void ping(String accessToken) {
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + accessToken))
                .build();
        try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            var response = client.callTool(McpSchema.CallToolRequest.builder("ping").arguments(Map.of()).build());
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).containsExactly(new McpSchema.TextContent("pong"));
        }
    }

    private String register() throws Exception {
        var response = post(jsonMapper.writeValueAsString(Map.of("redirect_uris", List.of(redirectUri))));
        assertThat(response.statusCode()).isEqualTo(201);
        var registration = json(response);
        assertThat(registration).containsEntry("redirect_uris", List.of(redirectUri))
                .containsEntry("token_endpoint_auth_method", "none");
        assertThat(registration.get("client_id")).isInstanceOf(String.class);
        return (String) registration.get("client_id");
    }

    private Map<String, String> parameters(String clientId) {
        return new LinkedHashMap<>(Map.of(
                "client_id", clientId,
                "redirect_uri", redirectUri,
                "state", state,
                "response_type", "code",
                "code_challenge", "challenge-1",
                "code_challenge_method", "S256",
                "resource", config.getMcpUrl()));
    }

    private HttpResponse<String> authorize(Map<String, String> parameters) throws Exception {
        String query = parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return get("/oauth/authorize?" + query);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/oauth/register"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> json(HttpResponse<String> response) throws Exception {
        return jsonMapper.readValue(response.body(), new TypeRef<Map<String, Object>>() {});
    }

    private Map<String, String> query(URI location) {
        return Arrays.stream(location.getRawQuery().split("&"))
                .map(value -> value.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
