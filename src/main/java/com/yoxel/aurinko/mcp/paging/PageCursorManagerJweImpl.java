package com.yoxel.aurinko.mcp.paging;

import com.yoxel.aurinko.mcp.oauth.JweHelper;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class PageCursorManagerJweImpl implements PageCursorManager {

    private static final Duration LIFETIME = Duration.ofHours(1);

    private final JweHelper jweHelper;

    @Override
    public String create(PageScope scope, PageCursor cursor) {
        if (cursor.getOffset() < 0) {
            throw new InvalidPageCursorException();
        }
        try {
            Map<String, Object> position = new LinkedHashMap<>();
            position.put("pageToken", cursor.getPageToken());
            position.put("offset", cursor.getOffset());
            return jweHelper.encrypt("page", Map.of("scope", Map.of("userId", scope.getUserId(),
                    "accountId", scope.getAccountId(), "tool", scope.getTool(), "parameters", scope.getParameters()),
                    "cursor", position), LIFETIME);
        } catch (RuntimeException e) {
            throw new InvalidPageCursorException();
        }
    }

    @Override
    public PageCursor parse(String value, PageScope scope) throws InvalidPageCursorException {
        try {
            var data = jweHelper.decrypt(value, "page");
            if (!(data.get("scope") instanceof Map<?, ?> storedScope)
                    || storedScope.size() != 4
                    || !scope.getUserId().equals(storedScope.get("userId"))
                    || !(storedScope.get("accountId") instanceof Number accountId)
                    || new BigDecimal(accountId.toString()).longValueExact() != scope.getAccountId()
                    || !scope.getTool().equals(storedScope.get("tool"))
                    || !scope.getParameters().equals(storedScope.get("parameters"))
                    || !(data.get("cursor") instanceof Map<?, ?> cursor)
                    || !cursor.containsKey("pageToken")
                    || !(cursor.get("offset") instanceof Number offset)) {
                throw new InvalidPageCursorException();
            }
            Object pageToken = cursor.get("pageToken");
            int position = new BigDecimal(offset.toString()).intValueExact();
            if (position < 0 || (pageToken != null && !(pageToken instanceof String))) {
                throw new InvalidPageCursorException();
            }
            return new PageCursor((String) pageToken, position);
        } catch (RuntimeException e) {
            throw new InvalidPageCursorException();
        }
    }
}
