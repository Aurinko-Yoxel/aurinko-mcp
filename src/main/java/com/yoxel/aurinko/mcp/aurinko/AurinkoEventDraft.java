package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
public class AurinkoEventDraft {

    @ToString.Exclude
    String subject;
    @ToString.Exclude
    String description;
    @ToString.Exclude
    String location;
    OffsetDateTime start;
    OffsetDateTime end;
}
