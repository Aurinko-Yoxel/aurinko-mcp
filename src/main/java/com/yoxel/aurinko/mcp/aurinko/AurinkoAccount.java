package com.yoxel.aurinko.mcp.aurinko;

import lombok.Value;

@Value
public class AurinkoAccount {

    long id;
    String serviceType;
    String email;
    boolean active;
}
