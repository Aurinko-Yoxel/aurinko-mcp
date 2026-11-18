package com.yoxel.aurinko.mcp.aurinko;

import com.google.api.client.http.HttpResponseException;

public class AurinkoException extends RuntimeException {

    public AurinkoException() {
        super("Aurinko authentication failed");
    }

    public AurinkoException(Throwable cause) {
        super("Aurinko authentication failed", cause);
    }

    public Integer getStatusCode() {
        for (Throwable current = getCause(); current != null; current = current.getCause()) {
            if (current instanceof HttpResponseException responseException) {
                return responseException.getStatusCode();
            }
        }
        return null;
    }
}
