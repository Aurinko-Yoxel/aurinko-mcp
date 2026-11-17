package com.yoxel.aurinko.mcp.oauth;

import java.net.URI;
import java.util.Map;

public class LoginPageRendererImpl implements LoginPageRenderer {

    @Override
    public String render(AuthorizationRequest request, Map<String, String> authorizeUrls) {
        String host = URI.create(request.getRedirectUri()).getHost();
        StringBuilder buttons = new StringBuilder();
        authorizeUrls.forEach((serviceType, url) -> {
            String provider = "Office365".equals(serviceType) ? "Microsoft" : serviceType;
            buttons.append("<a class=\"provider\" role=\"button\" href=\"").append(escape(url))
                    .append("\">Sign in with ").append(escape(provider)).append("</a>");
        });
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Sign in to Aurinko</title>
                <style>
                body { font: 16px system-ui, sans-serif; margin: 48px auto; padding: 24px; max-width: 480px; }
                .provider { display: block; padding: 14px; margin: 12px 0; border: 1px solid #888;
                    border-radius: 8px; text-align: center; color: #124a88; text-decoration: none; }
                </style>
                </head>
                <body>
                <h1>Sign in to Aurinko</h1>
                <p>The application at <strong>%s</strong> is requesting access.</p>
                %s
                </body>
                </html>
                """.formatted(escape(host), buttons);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
