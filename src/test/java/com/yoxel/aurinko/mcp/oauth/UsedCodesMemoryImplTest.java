package com.yoxel.aurinko.mcp.oauth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class UsedCodesMemoryImplTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-16T12:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now.get(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final UsedCodes usedCodes = new UsedCodesMemoryImpl(clock);

    @Test
    void customRetentionKeepsConnectionNonceForTenMinutes() {
        var connectionCodes = new UsedCodesMemoryImpl(clock, Duration.ofMinutes(10));
        assertThat(connectionCodes.markUsed("nonce")).isTrue();
        now.updateAndGet(value -> value.plus(Duration.ofMinutes(10).minusSeconds(1)));
        assertThat(connectionCodes.markUsed("nonce")).isFalse();
        now.updateAndGet(value -> value.plusSeconds(1));
        assertThat(connectionCodes.markUsed("nonce")).isTrue();
    }

    @Test
    void codeMarkedOnlyOnce() {
        assertThat(usedCodes.markUsed("code-1")).isTrue();
        assertThat(usedCodes.markUsed("code-1")).isFalse();
    }

    @Test
    void expiredRecordRemovedAfterTwoMinutes() {
        assertThat(usedCodes.markUsed("code-1")).isTrue();
        now.updateAndGet(value -> value.plus(Duration.ofMinutes(2).minusSeconds(1)));
        assertThat(usedCodes.markUsed("code-1")).isFalse();
        now.updateAndGet(value -> value.plusSeconds(1));
        assertThat(usedCodes.markUsed("code-1")).isTrue();
    }

    @Test
    void concurrentRequestsMarkCodeOnlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<Boolean>>();
            for (int index = 0; index < 32; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return usedCodes.markUsed("code-1");
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
