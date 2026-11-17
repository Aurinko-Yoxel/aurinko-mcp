package com.yoxel.aurinko.mcp.oauth;

import lombok.Value;

import java.time.Duration;

@Value
public class LoginState {

    public static final Duration LIFETIME = Duration.ofMinutes(10);

    AuthorizationRequest request;
    String nonce;
}
