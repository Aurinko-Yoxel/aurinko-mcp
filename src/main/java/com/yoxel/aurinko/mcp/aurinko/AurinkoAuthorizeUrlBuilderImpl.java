package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import lombok.RequiredArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AurinkoAuthorizeUrlBuilderImpl implements AurinkoAuthorizeUrlBuilder {

    private final AurinkoConfig config;
    private final ServerConfig serverConfig;
    private final UserSignatureHelper signatureHelper;
    private final Clock clock;

    @Override
    public String build(String serviceType, String returnUrl, String state) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("clientId", config.getClientId());
        parameters.put("serviceType", serviceType);
        parameters.put("accountRole", "primary");
        parameters.put("responseType", "code");
        parameters.put("returnUrl", returnUrl);
        parameters.put("state", state);
        if (config.getLoginScopes() != null) {
            parameters.put("scopes", config.getLoginScopes());
        }
        return url(parameters);
    }

    @Override
    public String buildConnectUrl(String serviceType, String userId, String state) {
        String scopes = switch (serviceType) {
            case "IMAP" -> config.getImapScopes();
            case "CalDav" -> config.getCaldavScopes();
            default -> throw new IllegalArgumentException("Unsupported connection service type");
        };
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("clientId", config.getClientId());
        parameters.put("serviceType", serviceType);
        parameters.put("accountRole", "secondary");
        parameters.put("responseType", "code");
        parameters.put("userId", userId);
        parameters.put("timestamp", Long.toString(timestamp));
        parameters.put("userSignature", signatureHelper.sign(userId, timestamp));
        parameters.put("returnUrl", serverConfig.getPublicUrl() + "/accounts/callback");
        parameters.put("state", state);
        parameters.put("scopes", scopes);
        return url(parameters);
    }

    private String url(Map<String, String> parameters) {
        return config.getUrl() + "/v1/auth/authorizeUser?" + parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
