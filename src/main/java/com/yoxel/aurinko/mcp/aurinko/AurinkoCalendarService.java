package com.yoxel.aurinko.mcp.aurinko;

import java.time.OffsetDateTime;
import java.util.List;

public interface AurinkoCalendarService {

    List<AurinkoEvent> listEvents(String userSession, long accountId, String calendarId,
            OffsetDateTime timeMin, OffsetDateTime timeMax, int limit) throws AurinkoException;

    String createEvent(String userSession, long accountId, String calendarId,
            AurinkoEventDraft draft) throws AurinkoException;
}
