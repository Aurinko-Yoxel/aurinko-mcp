package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.aurinko.AccountAccessDeniedException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import com.yoxel.aurinko.mcp.paging.PageCursor;
import com.yoxel.aurinko.mcp.paging.PageScope;
import com.yoxel.aurinko.mcp.paging.PageCursorManager;
import com.yoxel.aurinko.mcp.paging.InvalidPageCursorException;
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
public class SearchMessagesTool implements McpTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final AccountAccessValidator accessValidator;
    private final AurinkoEmailService emailService;
    private final McpJsonMapper jsonMapper;
    private final ToolDescription toolDescription;
    private final PageCursorManager cursorManager;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("search_messages", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("accountId"),
                                "properties", Map.of(
                                        "cursor", Map.of("type", "string", "description", toolDescription.parameter("cursor")),
                                        "accountId", Map.of("type", "integer", "description", toolDescription.parameter("accountId")),
                                        "query", Map.of("type", "string", "description", toolDescription.parameter("query")),
                                        "limit", Map.of("type", "integer", "minimum", MIN_LIMIT,
                                                "maximum", MAX_LIMIT, "default", DEFAULT_LIMIT, "description", toolDescription.parameter("limit")))))
                        .description(toolDescription.getDescription())
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).build())
                        .build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        var arguments = new ToolArguments(request.arguments());
                        long accountId = arguments.requireAccountId();
                        int limit = arguments.optionalInt("limit", DEFAULT_LIMIT, MIN_LIMIT, MAX_LIMIT);
                        String query = arguments.optionalString("query");
                        accessValidator.validate(principal.getUserId(), accountId);
                        var scope = new PageScope(principal.getUserId(), accountId, "search_messages",
                                Map.of("query", query == null || query.isBlank() ? "" : query));
                        String cursor = arguments.optionalString("cursor");
                        PageCursor start = cursor == null ? null : cursorManager.parse(cursor, scope);
                        var page = emailService.searchMessages(principal.getUserSession(), accountId, query, start, limit);
                        return ToolResultHelper.page(jsonMapper, page, cursorManager, scope);
                    } catch (InvalidPageCursorException e) {
                        return ToolResultHelper.error("Invalid or expired cursor");
                    } catch (InvalidArgumentException e) {
                        return ToolResultHelper.error(e.getMessage());
                    } catch (AccountAccessDeniedException e) {
                        return ToolResultHelper.error("Account not found");
                    } catch (AurinkoException | IOException e) {
                        ToolErrorLogger.log(log, "Failed to load messages from Aurinko", e);
                        return ToolResultHelper.error("Failed to load messages from Aurinko");
                    }
                })
                .build();
    }
}
