package com.yoxel.aurinko.mcp.oauth;

public class InvalidClientException extends RuntimeException {

    public InvalidClientException() {
        super("Invalid client_id");
    }

    public InvalidClientException(Throwable cause) {
        super("Invalid client_id", cause);
    }
}
