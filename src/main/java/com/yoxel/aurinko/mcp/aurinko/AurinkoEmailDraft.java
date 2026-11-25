package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

import java.util.List;

@Value
public class AurinkoEmailDraft {

    @ToString.Exclude
    List<String> to;
    @ToString.Exclude
    List<String> cc;
    @ToString.Exclude
    String subject;
    @ToString.Exclude
    String body;
}
