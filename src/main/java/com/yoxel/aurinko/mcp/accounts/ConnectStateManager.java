package com.yoxel.aurinko.mcp.accounts;

public interface ConnectStateManager {

    String createState(String userId, String serviceType);

    ConnectState parseState(String state) throws InvalidConnectStateException;
}
