package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.aurinko.AccountAccessDeniedException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ListEventsTool implements McpTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;
    private static final Duration MAX_RANGE = Duration.ofDays(62);

    private final AccountAccessValidator accessValidator;
    private final AurinkoCalendarService calendarService;
    private final McpJsonMapper jsonMapper;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("list_events", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("accountId", "timeMin", "timeMax"),
                                "properties", Map.of(
                                        "accountId", Map.of("type", "integer"),
                                        "calendarId", Map.of("type", "string"),
                                        "timeMin", Map.of("type", "string", "description", "ISO-8601 date-time with offset"),
                                        "timeMax", Map.of("type", "string", "description", "ISO-8601 date-time with offset"),
                                        "limit", Map.of("type", "integer", "minimum", MIN_LIMIT,
                                                "maximum", MAX_LIMIT, "default", DEFAULT_LIMIT))))
                        .description("List calendar events in a time range for one of the user's connected accounts")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).build())
                        .build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        var arguments = new ToolArguments(request.arguments());
                        long accountId = arguments.requireAccountId();
                        String calendarId = arguments.optionalString("calendarId");
                        var timeMin = arguments.requireDateTime("timeMin");
                        var timeMax = arguments.requireDateTime("timeMax");
                        int limit = arguments.optionalInt("limit", DEFAULT_LIMIT, MIN_LIMIT, MAX_LIMIT);
                        if (!timeMax.isAfter(timeMin)) {
                            throw new InvalidArgumentException("timeMax must be after timeMin");
                        }
                        if (Duration.between(timeMin, timeMax).compareTo(MAX_RANGE) > 0) {
                            throw new InvalidArgumentException("Time range must not exceed 62 days");
                        }
                        accessValidator.validate(principal.getUserId(), accountId);
                        return ToolResultHelper.json(jsonMapper, calendarService.listEvents(
                                principal.getUserSession(), accountId, calendarId, timeMin, timeMax, limit));
                    } catch (InvalidArgumentException e) {
                        return ToolResultHelper.error(e.getMessage());
                    } catch (AccountAccessDeniedException e) {
                        return ToolResultHelper.error("Account not found");
                    } catch (AurinkoException | IOException e) {
                        ToolErrorLogger.log(log, "Failed to load events from Aurinko", e);
                        return ToolResultHelper.error("Failed to load events from Aurinko");
                    }
                })
                .build();
    }
}
