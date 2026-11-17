package com.yoxel.aurinko.mcp.accounts;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URI;

final class ConnectCookie {

    static final String NAME = "aurinko_connect_nonce";

    static void set(HttpServletResponse response, ServerConfig config, String nonce, int maxAge) {
        var cookie = new Cookie(NAME, nonce);
        cookie.setHttpOnly(true);
        cookie.setSecure("https".equals(URI.create(config.getPublicUrl()).getScheme()));
        cookie.setAttribute("SameSite", "Lax");
        cookie.setPath(URI.create(config.getPublicUrl()).getPath() + "/accounts/callback");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}
