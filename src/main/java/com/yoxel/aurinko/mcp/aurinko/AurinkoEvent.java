package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

@Value
public class AurinkoEvent {

    String id;
    String calendarId;
    @ToString.Exclude
    String subject;
    @ToString.Exclude
    String description;
    @ToString.Exclude
    String location;
    String start;
    String end;
    boolean allDay;
    String organizer;
    String htmlLink;
}
