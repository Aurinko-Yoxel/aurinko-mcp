package com.yoxel.aurinko.mcp.auth;

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public class McpSessionOwnersMemoryImpl implements McpSessionOwners {

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final int MAX_SESSIONS = 10_000;

    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    @Override
    public synchronized void register(String sessionId, McpSessionOwner owner) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(owner);
        Instant now = clock.instant();
        Entry current = entries.get(sessionId);
        if (current != null && !current.getExpiresAt().isAfter(now)) {
            entries.remove(sessionId);
            current = null;
        }
        if (current != null && !current.getOwner().equals(owner)) {
            return;
        }
        if (current == null && entries.size() >= MAX_SESSIONS) {
            entries.entrySet().removeIf(entry -> !entry.getValue().getExpiresAt().isAfter(now));
            if (entries.size() >= MAX_SESSIONS) {
                var iterator = entries.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
        entries.remove(sessionId);
        entries.put(sessionId, new Entry(owner, now.plus(SESSION_TTL)));
    }

    @Override
    public synchronized boolean isOwner(String sessionId, McpSessionOwner owner) {
        Entry entry = entries.get(sessionId);
        if (entry == null) {
            return false;
        }
        Instant now = clock.instant();
        if (!entry.getExpiresAt().isAfter(now)) {
            entries.remove(sessionId);
            return false;
        }
        if (!entry.getOwner().equals(owner)) {
            return false;
        }
        entries.remove(sessionId);
        entries.put(sessionId, new Entry(owner, now.plus(SESSION_TTL)));
        return true;
    }

    @Override
    public synchronized void remove(String sessionId) {
        entries.remove(sessionId);
    }

    @Value
    private static class Entry {

        McpSessionOwner owner;
        Instant expiresAt;
    }
}
