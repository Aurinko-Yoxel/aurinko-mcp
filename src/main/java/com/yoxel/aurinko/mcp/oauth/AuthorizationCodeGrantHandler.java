package com.yoxel.aurinko.mcp.oauth;

import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class AuthorizationCodeGrantHandler implements GrantHandler {

    private final AuthorizationCodeManager codeManager;
    private final TokenManager tokenManager;
    private final PkceVerifier pkceVerifier;
    private final UsedCodes usedCodes;

    @Override
    public String getGrantType() {
        return "authorization_code";
    }

    @Override
    public TokenResponse handle(Map<String, String> parameters) throws OAuthErrorException {
        for (String name : List.of("code", "client_id", "redirect_uri", "code_verifier")) {
            if (parameters.get(name) == null || parameters.get(name).isBlank()) {
                throw new OAuthErrorException("invalid_request", "Missing required parameter: " + name);
            }
        }
        String value = parameters.get("code");
        AuthorizationCode code;
        try {
            code = codeManager.parseCode(value);
        } catch (InvalidGrantException e) {
            throw new OAuthErrorException("invalid_grant", "Invalid or expired authorization code");
        }
        if (!code.getClientId().equals(parameters.get("client_id"))) {
            throw new OAuthErrorException("invalid_grant", "Authorization code client does not match");
        }
        if (!code.getRedirectUri().equals(parameters.get("redirect_uri"))) {
            throw new OAuthErrorException("invalid_grant", "Authorization code redirect URI does not match");
        }
        if (!pkceVerifier.verify(parameters.get("code_verifier"), code.getCodeChallenge())) {
            throw new OAuthErrorException("invalid_grant", "Invalid PKCE verifier");
        }
        if (parameters.containsKey("resource") && !code.getResource().equals(parameters.get("resource"))) {
            throw new OAuthErrorException("invalid_grant", "Authorization code resource does not match");
        }
        if (!usedCodes.markUsed(value)) {
            throw new OAuthErrorException("invalid_grant", "Authorization code has already been used");
        }
        var principal = new McpPrincipal(code.getUserId(), code.getUserSession(), code.getClientId());
        return new TokenResponse(
                tokenManager.createAccessToken(principal),
                tokenManager.createRefreshToken(principal),
                tokenManager.getAccessTokenLifetime().getSeconds());
    }
}
