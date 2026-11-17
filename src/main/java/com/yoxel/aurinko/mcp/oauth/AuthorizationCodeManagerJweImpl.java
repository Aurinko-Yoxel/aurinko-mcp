package com.yoxel.aurinko.mcp.oauth;

import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class AuthorizationCodeManagerJweImpl implements AuthorizationCodeManager {

    private final JweHelper jweHelper;

    @Override
    public String createCode(AuthorizationRequest request, String userId, String userSession) {
        return jweHelper.encrypt("code", Map.of(
                "userId", userId,
                "userSession", userSession,
                "clientId", request.getClientId(),
                "redirectUri", request.getRedirectUri(),
                "codeChallenge", request.getCodeChallenge(),
                "resource", request.getResource()), Duration.ofSeconds(60));
    }

    @Override
    public AuthorizationCode parseCode(String code) throws InvalidGrantException {
        try {
            var data = jweHelper.decrypt(code, "code");
            if (List.of("userId", "userSession", "clientId", "redirectUri", "codeChallenge", "resource").stream()
                    .anyMatch(name -> !(data.get(name) instanceof String value) || value.isBlank())) {
                throw new InvalidGrantException();
            }
            return new AuthorizationCode((String) data.get("userId"), (String) data.get("userSession"),
                    (String) data.get("clientId"),
                    (String) data.get("redirectUri"), (String) data.get("codeChallenge"), (String) data.get("resource"));
        } catch (JweException e) {
            throw new InvalidGrantException(e);
        }
    }
}
