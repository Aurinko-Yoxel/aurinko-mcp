package com.yoxel.aurinko.mcp.oauth;

public interface PkceVerifier {

    boolean verify(String codeVerifier, String codeChallenge);
}
