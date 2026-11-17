package com.yoxel.aurinko.mcp.oauth;

import java.util.List;

public interface ClientRegistrationManager {

    String register(List<String> redirectUris);

    ClientRegistration find(String clientId) throws InvalidClientException;
}
