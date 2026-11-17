package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.AurUserSessionAuthorization;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class AurinkoClientFactoryImpl implements AurinkoClientFactory {

    private final AurinkoConfig config;

    @Override
    public AurinkoService create(String userSession, long accountId) {
        return AurinkoService.create(config.getUrl(), new AurUserSessionAuthorization(userSession),
                Map.of("X-Aurinko-Account-Id", String.valueOf(accountId)), "aurinko-mcp");
    }
}
