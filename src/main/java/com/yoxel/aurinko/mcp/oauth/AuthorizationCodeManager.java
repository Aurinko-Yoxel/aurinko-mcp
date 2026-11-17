package com.yoxel.aurinko.mcp.oauth;

public interface AuthorizationCodeManager {

    String createCode(AuthorizationRequest request, String userId, String userSession);

    AuthorizationCode parseCode(String code) throws InvalidGrantException;
}
