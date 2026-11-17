package com.yoxel.aurinko.mcp.accounts;

import java.util.List;
import java.util.stream.Collectors;

public class ConnectPageRenderer {

    public String confirmPage(String serviceType, List<String> connectedEmails, String aurinkoUrl) {
        String emails = connectedEmails.stream().map(email -> "<li>" + escape(email) + "</li>")
                .collect(Collectors.joining());
        return page("Connect a mailbox to Aurinko", "<h1>Connect a mailbox to Aurinko</h1>"
                + "<p>Service: " + escape(serviceType) + "</p>"
                + "<p>This will add an account to the user that already has:</p><ul>" + emails + "</ul>"
                + "<a class=\"button\" role=\"button\" href=\"" + escape(aurinkoUrl) + "\">Continue</a>");
    }

    public String resultPage(boolean success) {
        String message = success ? "Account connected. You can return to the chat."
                : "Account was not connected. Please try again from the chat.";
        return page(success ? "Account connected" : "Account was not connected", "<h1>" + message + "</h1>");
    }

    private String page(String title, String content) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                body { font: 16px system-ui, sans-serif; margin: 48px auto; padding: 24px; max-width: 520px; }
                .button { display: inline-block; padding: 12px 20px; border: 1px solid #888;
                    border-radius: 8px; color: #124a88; text-decoration: none; }
                </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(escape(title), content);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
