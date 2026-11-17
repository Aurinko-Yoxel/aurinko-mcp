package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

import java.util.List;

@Value
public class AurinkoEmailSummary {

    String id;
    String threadId;
    @ToString.Exclude
    String subject;
    String from;
    List<String> to;
    String receivedAt;
    boolean hasAttachments;
}
