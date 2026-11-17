package com.yoxel.aurinko.mcp.oauth;

public interface LoginStateManager {

    String createState(LoginState state);

    LoginState parseState(String state) throws InvalidLoginStateException;
}
