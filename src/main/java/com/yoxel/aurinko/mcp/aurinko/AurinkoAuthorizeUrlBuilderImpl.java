package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import lombok.RequiredArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AurinkoAuthorizeUrlBuilderImpl implements AurinkoAuthorizeUrlBuilder {

    private final AurinkoConfig config;

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
        return config.getUrl() + "/v1/auth/authorizeUser?" + parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
