package com.yoxel.aurinko.mcp.oauth;

public class JweException extends RuntimeException {

    public JweException() {
        super("Invalid encrypted value");
    }

    public JweException(Throwable cause) {
        super("Invalid encrypted value", cause);
    }
}
