package com.yoxel.aurinko.mcp.support;

import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEvent;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEventDraft;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
public class FakeAurinkoCalendarService implements AurinkoCalendarService {

    private int calls;
    private String userSession;
    private long accountId;
    private String calendarId;
    private OffsetDateTime timeMin;
    private OffsetDateTime timeMax;
    private int limit;
    private AurinkoEventDraft draft;
    private AurinkoException failure;

    public void failWith(AurinkoException failure) {
        this.failure = failure;
    }

    @Override
    public List<AurinkoEvent> listEvents(String userSession, long accountId, String calendarId,
            OffsetDateTime timeMin, OffsetDateTime timeMax, int limit) {
        calls++;
        this.userSession = userSession;
        this.accountId = accountId;
        this.calendarId = calendarId;
        this.timeMin = timeMin;
        this.timeMax = timeMax;
        this.limit = limit;
        if (failure != null) {
            throw failure;
        }
        return List.of(new AurinkoEvent("event-1", "primary", "Private subject", "Private description", "Private location",
                timeMin.toString(), timeMax.toString(), false, "alice@example.com", "https://example.com/event"));
    }

    @Override
    public String createEvent(String userSession, long accountId, String calendarId, AurinkoEventDraft draft) {
        calls++;
        this.userSession = userSession;
        this.accountId = accountId;
        this.calendarId = calendarId;
        this.draft = draft;
        if (failure != null) {
            throw failure;
        }
        return "created-event";
    }
}
