package com.yoxel.aurinko.mcp.oauth;

import lombok.Getter;

@Getter
public class OAuthErrorException extends RuntimeException {

    private final String error;
    private final String errorDescription;

    public OAuthErrorException(String error, String errorDescription) {
        super(errorDescription);
        this.error = error;
        this.errorDescription = errorDescription;
    }
}
