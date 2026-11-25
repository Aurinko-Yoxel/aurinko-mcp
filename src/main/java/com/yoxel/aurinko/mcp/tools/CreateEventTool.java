package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.aurinko.AccountAccessDeniedException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEventDraft;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEventAttendee;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
public class CreateEventTool implements McpTool {

    private static final int MAX_ATTENDEES = 50;

    private final AccountAccessValidator accessValidator;
    private final AurinkoCalendarService calendarService;
    private final McpJsonMapper jsonMapper;
    private final ToolDescription toolDescription;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("create_event", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("accountId", "subject", "start", "end"),
                                "properties", Map.of(
                                        "accountId", Map.of("type", "integer", "description", toolDescription.parameter("accountId")),
                                        "calendarId", Map.of("type", "string", "description", toolDescription.parameter("calendarId")),
                                        "subject", Map.of("type", "string", "minLength", 1, "description", toolDescription.parameter("subject")),
                                        "start", Map.of("type", "string", "description", toolDescription.parameter("start")),
                                        "end", Map.of("type", "string", "description", toolDescription.parameter("end")),
                                        "description", Map.of("type", "string", "description", toolDescription.parameter("description")),
                                        "attendees", Map.of("type", "array", "maxItems", MAX_ATTENDEES,
                                                "description", toolDescription.parameter("attendees"),
                                                "items", Map.of("type", "object", "additionalProperties", false, "required", List.of("email"),
                                                        "properties", Map.of("email", Map.of("type", "string", "format", "email"),
                                                                "name", Map.of("type", "string")))),
                                        "notifyAttendees", Map.of("type", "boolean", "description", toolDescription.parameter("notifyAttendees")),
                                        "location", Map.of("type", "string", "description", toolDescription.parameter("location")))))
                        .description(toolDescription.getDescription())
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).destructiveHint(false).build())
                        .build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        var arguments = new ToolArguments(request.arguments());
                        long accountId = arguments.requireAccountId();
                        String calendarId = arguments.optionalString("calendarId");
                        String subject = arguments.requireString("subject");
                        var start = arguments.requireDateTime("start");
                        var end = arguments.requireDateTime("end");
                        if (!end.isAfter(start)) {
                            throw new InvalidArgumentException("end must be after start");
                        }
                        var attendees = attendees(arguments);
                        Boolean notify = arguments.optionalBoolean("notifyAttendees");
                        if (!attendees.isEmpty() && notify == null) {
                            throw new InvalidArgumentException("notifyAttendees is required when attendees are given");
                        }
                        var draft = new AurinkoEventDraft(subject, arguments.optionalString("description"),
                                arguments.optionalString("location"), start, end, attendees, notify);
                        accessValidator.validate(principal.getUserId(), accountId);
                        String id = calendarService.createEvent(principal.getUserSession(), accountId, calendarId, draft);
                        return ToolResultHelper.json(jsonMapper, Map.of("id", id));
                    } catch (InvalidArgumentException e) {
                        return ToolResultHelper.error(e.getMessage());
                    } catch (AccountAccessDeniedException e) {
                        return ToolResultHelper.error("Account not found");
                    } catch (AurinkoException | IOException e) {
                        ToolErrorLogger.log(log, "Failed to create event in Aurinko", e);
                        return ToolResultHelper.error("Failed to create event in Aurinko");
                    }
                })
                .build();
    }
    private List<AurinkoEventAttendee> attendees(ToolArguments arguments) {
        var values = arguments.optionalList("attendees");
        if (values.size() > MAX_ATTENDEES) {
            throw new InvalidArgumentException("Too many attendees");
        }
        var emails = new HashSet<String>();
        return values.stream().map(value -> {
            if (!(value instanceof Map<?, ?> person) || !(person.get("email") instanceof String email)
                    || !EmailAddressValidator.isValid(email)) {
                throw new InvalidArgumentException("Invalid attendee email");
            }
            if (!emails.add(email.toLowerCase(Locale.ROOT))) {
                throw new InvalidArgumentException("Duplicate attendee email");
            }
            if (person.containsKey("name") && !(person.get("name") instanceof String)) {
                throw new InvalidArgumentException("Attendee name must be a string");
            }
            return new AurinkoEventAttendee(email, (String) person.get("name"));
        }).toList();
    }

}
