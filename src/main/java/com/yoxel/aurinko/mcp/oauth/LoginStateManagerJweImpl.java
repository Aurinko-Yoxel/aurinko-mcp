package com.yoxel.aurinko.mcp.oauth;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class LoginStateManagerJweImpl implements LoginStateManager {

    private final JweHelper jweHelper;

    @Override
    public String createState(LoginState state) {
        var request = state.getRequest();
        Map<String, Object> data = new HashMap<>(Map.of(
                "clientId", request.getClientId(), "redirectUri", request.getRedirectUri(),
                "codeChallenge", request.getCodeChallenge(), "resource", request.getResource(),
                "nonce", state.getNonce()));
        if (request.getState() != null) {
            data.put("state", request.getState());
        }
        try {
            return jweHelper.encrypt("login", data, LoginState.LIFETIME);
        } catch (JweException e) {
            throw new InvalidLoginStateException(e);
        }
    }

    @Override
    public LoginState parseState(String state) throws InvalidLoginStateException {
        try {
            var data = jweHelper.decrypt(state, "login");
            if (List.of("clientId", "redirectUri", "codeChallenge", "resource", "nonce").stream()
                    .anyMatch(name -> !(data.get(name) instanceof String value) || value.isBlank())
                    || (data.containsKey("state") && !(data.get("state") instanceof String))) {
                throw new InvalidLoginStateException();
            }
            return new LoginState(new AuthorizationRequest((String) data.get("clientId"),
                    (String) data.get("redirectUri"), (String) data.get("state"),
                    (String) data.get("codeChallenge"), (String) data.get("resource")), (String) data.get("nonce"));
        } catch (JweException e) {
            throw new InvalidLoginStateException(e);
        }
    }
}
