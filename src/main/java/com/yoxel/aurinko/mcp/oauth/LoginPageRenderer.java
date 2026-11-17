package com.yoxel.aurinko.mcp.oauth;

import java.util.Map;

public interface LoginPageRenderer {

    String render(AuthorizationRequest request, Map<String, String> authorizeUrls);
}
