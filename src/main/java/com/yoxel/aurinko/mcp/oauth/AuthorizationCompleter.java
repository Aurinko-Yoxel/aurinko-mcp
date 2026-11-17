package com.yoxel.aurinko.mcp.oauth;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface AuthorizationCompleter {

    void complete(AuthorizationRequest request, String userId, String userSession, HttpServletResponse response) throws IOException;
}
