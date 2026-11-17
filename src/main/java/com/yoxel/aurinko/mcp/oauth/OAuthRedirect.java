package com.yoxel.aurinko.mcp.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class OAuthRedirect {

    static String location(String redirectUri, String name, String value, String state) {
        String query = URI.create(redirectUri).getRawQuery();
        String separator = query == null ? "?" : query.isEmpty() ? "" : "&";
        String location = redirectUri + separator + encode(name) + "=" + encode(value);
        if (state != null) {
            location += "&state=" + encode(state);
        }
        return location;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
