package com.yoxel.aurinko.mcp.oauth;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ClientRegistrationManagerJweImpl implements ClientRegistrationManager {

    private final JweHelper jweHelper;

    @Override
    public String register(List<String> redirectUris) {
        try {
            return jweHelper.encrypt("client", Map.of("redirectUris", List.copyOf(redirectUris)), null);
        } catch (JweException e) {
            throw new InvalidClientException(e);
        }
    }

    @Override
    public ClientRegistration find(String clientId) throws InvalidClientException {
        try {
            Object value = jweHelper.decrypt(clientId, "client").get("redirectUris");
            if (!(value instanceof List<?> uris) || uris.isEmpty()
                    || uris.stream().anyMatch(uri -> !(uri instanceof String))) {
                throw new InvalidClientException();
            }
            return new ClientRegistration(uris.stream().map(String.class::cast).toList());
        } catch (JweException e) {
            throw new InvalidClientException(e);
        }
    }
}
