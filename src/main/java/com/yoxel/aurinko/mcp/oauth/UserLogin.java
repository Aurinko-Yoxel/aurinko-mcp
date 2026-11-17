package com.yoxel.aurinko.mcp.oauth;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface UserLogin {

    void start(AuthorizationRequest request, HttpServletResponse response) throws IOException;
}
