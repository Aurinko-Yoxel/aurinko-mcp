package com.yoxel.aurinko.mcp.oauth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class UsedCodesMemoryImpl implements UsedCodes {

    private static final Duration RETENTION = Duration.ofMinutes(2);

    private final Clock clock;
    private final Duration retention;
    private final Map<String, Instant> usedCodes = new HashMap<>();

    public UsedCodesMemoryImpl(Clock clock) {
        this(clock, RETENTION);
    }

    public UsedCodesMemoryImpl(Clock clock, Duration retention) {
        this.clock = clock;
        this.retention = retention;
    }

    @Override
    public synchronized boolean markUsed(String code) {
        Instant now = clock.instant();
        usedCodes.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        return usedCodes.putIfAbsent(code, now.plus(retention)) == null;
    }
}
