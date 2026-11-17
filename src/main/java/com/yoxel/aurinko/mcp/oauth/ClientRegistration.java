package com.yoxel.aurinko.mcp.oauth;

import lombok.Value;

import java.util.List;

@Value
public class ClientRegistration {

    List<String> redirectUris;

    public ClientRegistration(List<String> redirectUris) {
        this.redirectUris = List.copyOf(redirectUris);
    }
}
