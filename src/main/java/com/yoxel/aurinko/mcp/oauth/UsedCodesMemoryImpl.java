package com.yoxel.aurinko.mcp.oauth;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class UsedCodesMemoryImpl implements UsedCodes {

    private static final Duration RETENTION = Duration.ofMinutes(2);

    private final Clock clock;
    private final Map<String, Instant> usedCodes = new HashMap<>();

    @Override
    public synchronized boolean markUsed(String code) {
        Instant now = clock.instant();
        usedCodes.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        return usedCodes.putIfAbsent(code, now.plus(RETENTION)) == null;
    }
}
