package com.yoxel.aurinko.mcp.aurinko;

import com.google.api.client.util.DateTime;
import com.yoxel.aurinko.apis.QueryParams;
import com.yoxel.aurinko.bean.AurEvent;
import com.yoxel.aurinko.bean.sub.EmailAddress;
import com.yoxel.aurinko.bean.sub.EventDateTime;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class AurinkoCalendarServiceImpl implements AurinkoCalendarService {

    private final AurinkoClientFactory clientFactory;

    @Override
    public List<AurinkoEvent> listEvents(String userSession, long accountId, String calendarId,
            OffsetDateTime timeMin, OffsetDateTime timeMax, int limit) throws AurinkoException {
        try (var service = clientFactory.create(userSession, accountId)) {
            String selectedCalendar = calendarId == null ? "primary" : calendarId;
            var events = service.calendars.calendarEvents(selectedCalendar);
            var query = QueryParams.of(QueryParams.qp("timeMin", timeMin.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                    QueryParams.qp("timeMax", timeMax.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            List<AurinkoEvent> result = new ArrayList<>();
            String pageToken = null;
            while (result.size() < limit) {
                var page = events.httpGet(events.entityPath() + "/range", query.add("pageToken", pageToken))
                        .parseAs(events.entityPageClass());
                if (page.getRecords() == null || page.getRecords().length == 0) {
                    break;
                }
                for (var event : page.getRecords()) {
                    result.add(map(event, selectedCalendar));
                    if (result.size() == limit) {
                        return result;
                    }
                }
                pageToken = page.getNextPageToken();
                if (pageToken == null) {
                    break;
                }
            }
            return result;
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }

    @Override
    public String createEvent(String userSession, long accountId, String calendarId,
            AurinkoEventDraft draft) throws AurinkoException {
        try (var service = clientFactory.create(userSession, accountId)) {
            var event = new AurEvent();
            event.setSubject(draft.getSubject());
            event.setDescription(draft.getDescription());
            event.setLocation(draft.getLocation());
            event.setStart(dateTime(draft.getStart()));
            event.setEnd(dateTime(draft.getEnd()));
            String id = service.calendars.calendarEvents(calendarId == null ? "primary" : calendarId).create(event).getId();
            if (id == null || id.isBlank()) {
                throw new AurinkoException();
            }
            return id;
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }

    private EventDateTime dateTime(OffsetDateTime value) {
        var date = new EventDateTime();
        date.setDateTime(new DateTime(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        return date;
    }

    private AurinkoEvent map(AurEvent event, String calendarId) {
        boolean allDay = event.getStart() != null && event.getStart().getDateOnly() != null
                || event.getEnd() != null && event.getEnd().getDateOnly() != null;
        return new AurinkoEvent(event.getId(), event.getCalendarId() == null ? calendarId : event.getCalendarId(),
                event.getSubject(), event.getDescription(), event.getLocation(), format(event.getStart()),
                format(event.getEnd()), allDay, event.getOrganizer() == null ? null
                        : address(event.getOrganizer().getEmailAddress()), event.getHtmlLink());
    }

    private String format(EventDateTime value) {
        if (value == null) {
            return null;
        }
        DateTime date = value.getDateOnly() == null ? value.getDateTime() : value.getDateOnly();
        return date == null ? null : date.toStringRfc3339();
    }

    private String address(EmailAddress value) {
        if (value == null) {
            return null;
        }
        if (value.getAddress() == null || value.getAddress().isBlank()) {
            return value.getRaw();
        }
        return value.getName() == null || value.getName().isBlank() ? value.getAddress()
                : value.getName() + " <" + value.getAddress() + ">";
    }
}
