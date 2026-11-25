package com.yoxel.aurinko.mcp.paging;

import lombok.ToString;
import lombok.Value;

@Value
public class PageCursor {

    @ToString.Exclude
    String pageToken;
    int offset;
}
