package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URI;

final class LoginCookie {

    static final String NAME = "aurinko_login_nonce";

    static void set(HttpServletResponse response, ServerConfig config, String nonce, int maxAge) {
        Cookie cookie = new Cookie(NAME, nonce);
        cookie.setHttpOnly(true);
        cookie.setSecure("https".equals(URI.create(config.getPublicUrl()).getScheme()));
        cookie.setAttribute("SameSite", "Lax");
        cookie.setPath(URI.create(config.getPublicUrl()).getPath() + "/oauth/aurinko/callback");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}
