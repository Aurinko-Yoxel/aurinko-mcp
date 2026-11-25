package com.yoxel.aurinko.mcp.aurinko;

import lombok.ToString;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

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
    @ToString.Exclude
    List<AurinkoEventAttendee> attendees;
    Boolean notifyAttendees;

    public AurinkoEventDraft(String subject, String description, String location, OffsetDateTime start, OffsetDateTime end) {
        this(subject, description, location, start, end, List.of(), null);
    }

    public AurinkoEventDraft(String subject, String description, String location, OffsetDateTime start, OffsetDateTime end,
            List<AurinkoEventAttendee> attendees, Boolean notifyAttendees) {
        this.subject = subject;
        this.description = description;
        this.location = location;
        this.start = start;
        this.end = end;
        this.attendees = List.copyOf(attendees);
        this.notifyAttendees = attendees.isEmpty() ? null : notifyAttendees;
    }
}
