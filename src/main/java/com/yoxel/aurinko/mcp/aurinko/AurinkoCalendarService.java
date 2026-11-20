package com.yoxel.aurinko.mcp.aurinko;

import java.time.OffsetDateTime;
import com.yoxel.aurinko.mcp.paging.PageCursor;
import com.yoxel.aurinko.mcp.paging.PageResult;

public interface AurinkoCalendarService {

    PageResult<AurinkoEvent> listEvents(String userSession, long accountId, String calendarId,
            OffsetDateTime timeMin, OffsetDateTime timeMax, PageCursor start, int limit) throws AurinkoException;

    String createEvent(String userSession, long accountId, String calendarId,
            AurinkoEventDraft draft) throws AurinkoException;
}
