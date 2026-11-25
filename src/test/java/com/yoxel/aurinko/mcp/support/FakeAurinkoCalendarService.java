package com.yoxel.aurinko.mcp.support;

import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEvent;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEventDraft;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import lombok.Getter;
import lombok.Setter;
import com.yoxel.aurinko.mcp.paging.PageResult;
import com.yoxel.aurinko.mcp.paging.PageCursor;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
public class FakeAurinkoCalendarService implements AurinkoCalendarService {

    private int calls;
    private PageCursor start;
    @Setter
    private PageCursor next;
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
    public PageResult<AurinkoEvent> listEvents(String userSession, long accountId, String calendarId,
            OffsetDateTime timeMin, OffsetDateTime timeMax, PageCursor start, int limit) {
        calls++;
        this.start = start;
        this.userSession = userSession;
        this.accountId = accountId;
        this.calendarId = calendarId;
        this.timeMin = timeMin;
        this.timeMax = timeMax;
        this.limit = limit;
        if (failure != null) {
            throw failure;
        }
        return new PageResult<>(List.of(new AurinkoEvent("event-1", "primary", "Private subject", "Private description", "Private location",
                timeMin.toString(), timeMax.toString(), false, "alice@example.com", "https://example.com/event")), next);
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
