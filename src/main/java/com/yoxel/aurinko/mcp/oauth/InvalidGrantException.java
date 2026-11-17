package com.yoxel.aurinko.mcp.oauth;

public class InvalidGrantException extends RuntimeException {

    public InvalidGrantException() {
        super("Invalid grant");
    }

    public InvalidGrantException(Throwable cause) {
        super("Invalid grant", cause);
    }
}
