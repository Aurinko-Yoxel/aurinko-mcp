package com.yoxel.aurinko.mcp.oauth;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@RequiredArgsConstructor
public class AuthorizationCompleterImpl implements AuthorizationCompleter {

    private final AuthorizationCodeManager codeManager;

    @Override
    public void complete(AuthorizationRequest request, String userId, String userSession, HttpServletResponse response)
            throws IOException {
        String code = codeManager.createCode(request, userId, userSession);
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(OAuthRedirect.location(request.getRedirectUri(), "code", code, request.getState()));
    }
}
