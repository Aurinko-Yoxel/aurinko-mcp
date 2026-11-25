package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

@Value
public class AurinkoEventAttendee {

    @ToString.Exclude
    String email;
    @ToString.Exclude
    String name;
}
