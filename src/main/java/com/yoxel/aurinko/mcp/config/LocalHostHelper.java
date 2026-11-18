package com.yoxel.aurinko.mcp.config;

import java.net.URI;

public class LocalHostHelper {

    private LocalHostHelper() {
    }

    public static boolean isLocal(URI uri) {
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }
}
