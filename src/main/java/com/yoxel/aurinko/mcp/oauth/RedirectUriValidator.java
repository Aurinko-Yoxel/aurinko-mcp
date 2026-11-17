package com.yoxel.aurinko.mcp.oauth;

public interface RedirectUriValidator {

    boolean isValid(String redirectUri);
}
