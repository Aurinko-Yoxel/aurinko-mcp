package com.yoxel.aurinko.mcp.paging;

import lombok.ToString;
import lombok.Value;

import java.util.Map;

@Value
public class PageScope {

    String userId;
    long accountId;
    String tool;
    @ToString.Exclude
    Map<String, String> parameters;

    public PageScope(String userId, long accountId, String tool, Map<String, String> parameters) {
        this.userId = userId;
        this.accountId = accountId;
        this.tool = tool;
        this.parameters = Map.copyOf(parameters);
    }
}
