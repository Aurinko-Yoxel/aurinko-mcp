package com.yoxel.aurinko.mcp.oauth;

import java.net.URI;

public class DefaultRedirectUriValidator implements RedirectUriValidator {

    @Override
    public boolean isValid(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(redirectUri);
            if (uri.getHost() == null || uri.getRawFragment() != null
                    || uri.getUserInfo() != null || uri.getPort() > 65535) {
                return false;
            }
            return "https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
