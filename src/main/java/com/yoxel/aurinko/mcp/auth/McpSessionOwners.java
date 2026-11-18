package com.yoxel.aurinko.mcp.auth;

public interface McpSessionOwners {

    void register(String sessionId, McpSessionOwner owner);

    boolean isOwner(String sessionId, McpSessionOwner owner);

    void remove(String sessionId);
}
