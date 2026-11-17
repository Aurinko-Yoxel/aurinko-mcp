package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

import java.util.List;

@Value
public class AurinkoEmail {

    String id;
    String threadId;
    @ToString.Exclude
    String subject;
    String from;
    List<String> to;
    String receivedAt;
    boolean hasAttachments;
    List<String> cc;
    @ToString.Exclude
    String body;
    boolean bodyTruncated;
}
