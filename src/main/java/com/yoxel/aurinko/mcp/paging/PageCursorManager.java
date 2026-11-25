package com.yoxel.aurinko.mcp.paging;

public interface PageCursorManager {

    String create(PageScope scope, PageCursor cursor);

    PageCursor parse(String value, PageScope scope) throws InvalidPageCursorException;
}
