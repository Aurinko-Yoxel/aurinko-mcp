package com.yoxel.aurinko.mcp.paging;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class PageTraversalTest {

    @Test
    void offsetWithinPagePreservedInContinuation() throws Exception {
        var result = PageTraversal.load(new PageCursor("page", 1), 2,
                token -> new PageTraversal.Page<>(List.of(0, 1, 2, 3), null));

        assertThat(result.getItems()).containsExactly(1, 2);
        assertThat(result.getNext()).isEqualTo(new PageCursor("page", 3));
    }

    @Test
    void firstPageCanStopMidPage() throws Exception {
        var result = PageTraversal.load(null, 2, token -> new PageTraversal.Page<>(List.of(0, 1, 2), null));

        assertThat(result.getItems()).containsExactly(0, 1);
        assertThat(result.getNext()).isEqualTo(new PageCursor(null, 2));
    }

    @Test
    void pageBoundaryReturnsNextPageAtOffsetZero() throws Exception {
        var calls = new AtomicInteger();
        var result = PageTraversal.load(null, 2, token -> {
            calls.incrementAndGet();
            return new PageTraversal.Page<>(List.of(0, 1), "second");
        });

        assertThat(result.getItems()).containsExactly(0, 1);
        assertThat(result.getNext()).isEqualTo(new PageCursor("second", 0));
        assertThat(calls).hasValue(1);
    }

    @Test
    void recordsCollectedAcrossPageBoundary() throws Exception {
        var pages = Map.of("first", new PageTraversal.Page<>(List.of(0, 1), "second"),
                "second", new PageTraversal.Page<>(List.of(2, 3), null));
        var result = PageTraversal.load(null, 3, token -> pages.get(token == null ? "first" : token));

        assertThat(result.getItems()).containsExactly(0, 1, 2);
        assertThat(result.getNext()).isEqualTo(new PageCursor("second", 1));
    }

    @Test
    void finalPageHasNoContinuation() throws Exception {
        var result = PageTraversal.load(null, 20, token -> new PageTraversal.Page<>(List.of(1, 2), null));

        assertThat(result.getItems()).containsExactly(1, 2);
        assertThat(result.getNext()).isNull();
    }

    @Test
    void infinitePagesLimitedToFiveRequests() throws Exception {
        var calls = new AtomicInteger();
        var result = PageTraversal.load(null, 20, token -> {
            int number = calls.incrementAndGet();
            return new PageTraversal.Page<>(List.of(number), "page-" + number);
        });

        assertThat(calls).hasValue(5);
        assertThat(result.getItems()).containsExactly(1, 2, 3, 4, 5);
        assertThat(result.getNext()).isEqualTo(new PageCursor("page-5", 0));
    }

    @Test
    void offsetPastChangedPageSkipsToNextPage() throws Exception {
        var calls = new AtomicInteger();
        var result = PageTraversal.load(new PageCursor(null, 99), 20, token -> {
            calls.incrementAndGet();
            return token == null ? new PageTraversal.Page<>(List.of(1), "second")
                    : new PageTraversal.Page<>(List.of(2, 3), null);
        });

        assertThat(result.getItems()).containsExactly(2, 3);
        assertThat(result.getNext()).isNull();
        assertThat(calls).hasValue(2);
    }

    @Test
    void emptyPageWithNextTokenContinued() throws Exception {
        var calls = new AtomicInteger();
        var result = PageTraversal.load(null, 20, token -> {
            calls.incrementAndGet();
            return token == null ? new PageTraversal.Page<>(List.of(), "second")
                    : new PageTraversal.Page<>(List.of(2), null);
        });

        assertThat(result.getItems()).containsExactly(2);
        assertThat(result.getNext()).isNull();
        assertThat(calls).hasValue(2);
    }

    @Test
    void emptyPagesCountTowardsRequestLimit() throws Exception {
        var calls = new AtomicInteger();
        var result = PageTraversal.load(null, 20, token ->
                new PageTraversal.Page<>(List.of(), "page-" + calls.incrementAndGet()));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getNext()).isEqualTo(new PageCursor("page-5", 0));
        assertThat(calls).hasValue(5);
    }
}
