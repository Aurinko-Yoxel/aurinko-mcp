package com.yoxel.aurinko.mcp.paging;

import lombok.ToString;
import lombok.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PageTraversal {

    private static final int MAX_REQUESTS = 5;

    private PageTraversal() {
    }

    public static <T> PageResult<T> load(PageCursor start, int limit, PageLoader<T> source) throws IOException {
        if (limit < 1 || (start != null && start.getOffset() < 0)) {
            throw new IllegalArgumentException("Invalid page limit or offset");
        }
        String token = start == null ? null : start.getPageToken();
        int offset = start == null ? 0 : start.getOffset();
        List<T> result = new ArrayList<>();
        for (int requests = 0; requests < MAX_REQUESTS; requests++) {
            var page = source.load(token);
            int position = Math.min(offset, page.getItems().size());
            int end = position + Math.min(limit - result.size(), page.getItems().size() - position);
            result.addAll(page.getItems().subList(position, end));
            if (end < page.getItems().size()) {
                return new PageResult<>(result, new PageCursor(token, end));
            }
            token = page.getNextPageToken();
            if (token == null) {
                return new PageResult<>(result, null);
            }
            offset = 0;
            if (result.size() == limit) {
                return new PageResult<>(result, new PageCursor(token, 0));
            }
        }
        return new PageResult<>(result, new PageCursor(token, 0));
    }

    @FunctionalInterface
    public interface PageLoader<T> {

        Page<T> load(String pageToken) throws IOException;
    }

    @Value
    public static class Page<T> {

        @ToString.Exclude
        List<T> items;
        @ToString.Exclude
        String nextPageToken;
    }
}
