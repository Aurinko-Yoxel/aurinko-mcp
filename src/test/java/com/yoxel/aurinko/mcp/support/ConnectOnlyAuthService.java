package com.yoxel.aurinko.mcp.support;

import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoLogin;

@FunctionalInterface
public interface ConnectOnlyAuthService extends AurinkoAuthService {

    @Override
    default AurinkoLogin exchangeCode(String code) {
        throw new AssertionError("Connection must not use the sign-in code exchange");
    }
}
