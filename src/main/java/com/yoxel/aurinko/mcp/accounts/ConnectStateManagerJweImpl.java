package com.yoxel.aurinko.mcp.accounts;

import com.yoxel.aurinko.mcp.oauth.JweException;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@RequiredArgsConstructor
public class ConnectStateManagerJweImpl implements ConnectStateManager {

    private final JweHelper jweHelper;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String createState(String userId, String serviceType) {
        if (userId == null || userId.isBlank() || (serviceType == null || serviceType.isBlank())) {
            throw new IllegalArgumentException("A user and supported connection service type are required");
        }
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        try {
            return jweHelper.encrypt("connect", Map.of("userId", userId, "serviceType", serviceType,
                    "nonce", Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)), ConnectState.LIFETIME);
        } catch (JweException e) {
            throw new InvalidConnectStateException(e);
        }
    }

    @Override
    public ConnectState parseState(String state) throws InvalidConnectStateException {
        try {
            var data = jweHelper.decrypt(state, "connect");
            if (!(data.get("userId") instanceof String userId) || userId.isBlank()
                    || !(data.get("serviceType") instanceof String serviceType)
                    || serviceType.isBlank()
                    || !(data.get("nonce") instanceof String nonce) || nonce.isBlank()) {
                throw new InvalidConnectStateException();
            }
            return new ConnectState(userId, serviceType, nonce);
        } catch (JweException e) {
            throw new InvalidConnectStateException(e);
        }
    }
}
