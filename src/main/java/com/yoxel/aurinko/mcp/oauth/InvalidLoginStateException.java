package com.yoxel.aurinko.mcp.oauth;

public class InvalidLoginStateException extends RuntimeException {

    public InvalidLoginStateException() {
        super("Invalid login state");
    }

    public InvalidLoginStateException(Throwable cause) {
        super("Invalid login state", cause);
    }
}
