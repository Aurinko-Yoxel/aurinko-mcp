package com.yoxel.aurinko.mcp.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

public class McpSessionOwnersMemoryImplTest {

    private final MutableClock clock = new MutableClock();
    private final McpSessionOwners owners = new McpSessionOwnersMemoryImpl(clock);
    private final McpSessionOwner owner = McpSessionOwner.of(new McpPrincipal("user", "private-session", "client"));

    @Test
    void ownerAccepted() {
        owners.register("session", owner);

        assertThat(owners.isOwner("session", owner)).isTrue();
    }

    @Test
    void differentUserRejected() {
        owners.register("session", owner);

        assertThat(owners.isOwner("session", new McpSessionOwner("other-user", "client"))).isFalse();
    }

    @Test
    void differentClientRejected() {
        owners.register("session", owner);

        assertThat(owners.isOwner("session", new McpSessionOwner("user", "other-client"))).isFalse();
    }

    @Test
    void unknownSessionRejected() {
        assertThat(owners.isOwner("unknown", owner)).isFalse();
    }

    @Test
    void idleSessionExpiresAfter24Hours() {
        owners.register("session", owner);
        clock.advance(Duration.ofHours(24));

        assertThat(owners.isOwner("session", owner)).isFalse();
    }

    @Test
    void ownerAccessExtendsLifetime() {
        owners.register("session", owner);
        clock.advance(Duration.ofHours(23));
        assertThat(owners.isOwner("session", owner)).isTrue();
        clock.advance(Duration.ofHours(2));
        assertThat(owners.isOwner("session", owner)).isTrue();
        clock.advance(Duration.ofHours(24));
        assertThat(owners.isOwner("session", owner)).isFalse();
    }

    @Test
    void differentOwnerDoesNotExtendLifetime() {
        owners.register("session", owner);
        clock.advance(Duration.ofHours(23));
        assertThat(owners.isOwner("session", new McpSessionOwner("other", "client"))).isFalse();
        clock.advance(Duration.ofHours(1));

        assertThat(owners.isOwner("session", owner)).isFalse();
    }

    @Test
    void removedSessionRejected() {
        owners.register("session", owner);
        owners.remove("session");

        assertThat(owners.isOwner("session", owner)).isFalse();
    }

    @Test
    void leastRecentlyAccessedSessionEvicted() {
        for (int i = 0; i < 10_000; i++) {
            owners.register("session-" + i, owner);
        }
        assertThat(owners.isOwner("session-0", owner)).isTrue();
        owners.register("new-session", owner);

        assertThat(owners.isOwner("session-1", owner)).isFalse();
        assertThat(owners.isOwner("session-0", owner)).isTrue();
        assertThat(owners.isOwner("new-session", owner)).isTrue();
    }

    @Test
    void expiredSessionsRemovedBeforeEviction() {
        owners.register("active-session", owner);
        for (int i = 1; i < 10_000; i++) {
            owners.register("session-" + i, owner);
        }
        clock.advance(Duration.ofHours(23));
        assertThat(owners.isOwner("active-session", owner)).isTrue();
        owners.register("recent-session", owner);
        clock.advance(Duration.ofHours(1));
        owners.register("new-session", owner);

        assertThat(owners.isOwner("active-session", owner)).isTrue();
        assertThat(owners.isOwner("recent-session", owner)).isTrue();
        assertThat(owners.isOwner("session-2", owner)).isFalse();
        assertThat(owners.isOwner("new-session", owner)).isTrue();
    }

    @Test
    void differentOwnerRegistrationDoesNotReplaceOwner() {
        owners.register("session", owner);
        var other = new McpSessionOwner("other", "client");
        owners.register("session", other);

        assertThat(owners.isOwner("session", other)).isFalse();
        assertThat(owners.isOwner("session", owner)).isTrue();
    }

    private static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
