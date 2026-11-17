package com.yoxel.aurinko.mcp.oauth;

import lombok.Value;

@Value
public class TokenResponse {

    String accessToken;
    String refreshToken;
    long expiresIn;
}
