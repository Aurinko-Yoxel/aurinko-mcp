package com.yoxel.aurinko.mcp.auth;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("Invalid access token");
    }

    public InvalidTokenException(Throwable cause) {
        super("Invalid access token", cause);
    }
}
