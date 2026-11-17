package com.yoxel.aurinko.mcp.oauth;

import java.util.Map;

public interface GrantHandler {

    String getGrantType();

    TokenResponse handle(Map<String, String> parameters) throws OAuthErrorException;
}
