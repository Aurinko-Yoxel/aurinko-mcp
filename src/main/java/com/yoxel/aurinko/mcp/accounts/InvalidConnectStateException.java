package com.yoxel.aurinko.mcp.accounts;

public class InvalidConnectStateException extends RuntimeException {

    public InvalidConnectStateException() {
        super("Invalid connection state");
    }

    public InvalidConnectStateException(Throwable cause) {
        super("Invalid connection state", cause);
    }
}
