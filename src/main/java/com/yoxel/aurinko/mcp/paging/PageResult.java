package com.yoxel.aurinko.mcp.paging;

import lombok.ToString;
import lombok.Value;

import java.util.List;

@Value
public class PageResult<T> {

    @ToString.Exclude
    List<T> items;
    PageCursor next;
}
