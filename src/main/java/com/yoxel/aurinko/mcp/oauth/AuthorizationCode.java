package com.yoxel.aurinko.mcp.oauth;

import lombok.ToString;
import lombok.Value;

@Value
public class AuthorizationCode {

    String userId;
    @ToString.Exclude
    String userSession;
    String clientId;
    String redirectUri;
    String codeChallenge;
    String resource;
}
