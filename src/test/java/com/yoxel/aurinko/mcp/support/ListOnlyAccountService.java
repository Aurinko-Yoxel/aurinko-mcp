package com.yoxel.aurinko.mcp.support;

import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;

@FunctionalInterface
public interface ListOnlyAccountService extends AurinkoAccountService {

    @Override
    default void deleteAccount(String userId, long accountId) {
        throw new AssertionError("Unexpected account deletion");
    }
}
