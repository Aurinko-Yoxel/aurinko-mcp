package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

@Value
public class AurinkoLogin {

    String userId;
    @ToString.Exclude
    String userSession;
    long accountId;
}
