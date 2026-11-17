package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.aurinko.AccountAccessDeniedException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEventDraft;
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

@Slf4j
@RequiredArgsConstructor
public class CreateEventTool implements McpTool {

    private final AccountAccessValidator accessValidator;
    private final AurinkoCalendarService calendarService;
    private final McpJsonMapper jsonMapper;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("create_event", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("accountId", "subject", "start", "end"),
                                "properties", Map.of(
                                        "accountId", Map.of("type", "integer"),
                                        "calendarId", Map.of("type", "string"),
                                        "subject", Map.of("type", "string", "minLength", 1),
                                        "start", Map.of("type", "string", "description", "ISO-8601 date-time with offset"),
                                        "end", Map.of("type", "string", "description", "ISO-8601 date-time with offset"),
                                        "description", Map.of("type", "string"),
                                        "location", Map.of("type", "string"))))
                        .description("Create a calendar event in one of the user's connected accounts")
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
                        var draft = new AurinkoEventDraft(subject, arguments.optionalString("description"),
                                arguments.optionalString("location"), start, end);
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
}
