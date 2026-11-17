package com.yoxel.aurinko.mcp.oauth;

import lombok.Value;

@Value
public class AuthorizationRequest {

    String clientId;
    String redirectUri;
    String state;
    String codeChallenge;
    String resource;
}
