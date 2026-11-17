package com.yoxel.aurinko.mcp.aurinko;

public interface AurinkoAuthorizeUrlBuilder {

    String build(String serviceType, String returnUrl, String state);
}
