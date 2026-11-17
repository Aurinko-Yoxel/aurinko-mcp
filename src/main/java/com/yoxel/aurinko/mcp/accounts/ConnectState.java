package com.yoxel.aurinko.mcp.accounts;

import lombok.ToString;
import lombok.Value;

import java.time.Duration;

@Value
public class ConnectState {

    public static final Duration LIFETIME = Duration.ofMinutes(10);

    String userId;
    String serviceType;
    @ToString.Exclude
    String nonce;
}
