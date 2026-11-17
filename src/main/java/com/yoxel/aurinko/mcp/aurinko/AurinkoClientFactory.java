package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.AurinkoService;

public interface AurinkoClientFactory {

    AurinkoService create(String userSession, long accountId);
}
