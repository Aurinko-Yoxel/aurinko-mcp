package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.aurinko.AccountAccessDeniedException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailService;
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
public class SearchMessagesTool implements McpTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final AccountAccessValidator accessValidator;
    private final AurinkoEmailService emailService;
    private final McpJsonMapper jsonMapper;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("search_messages", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("accountId"),
                                "properties", Map.of(
                                        "accountId", Map.of("type", "integer"),
                                        "query", Map.of("type", "string", "description",
                                                "Search query, for example: from:someone@example.com after:01/01/2026"),
                                        "limit", Map.of("type", "integer", "minimum", MIN_LIMIT,
                                                "maximum", MAX_LIMIT, "default", DEFAULT_LIMIT))))
                        .description("Search email messages in one of the user's connected accounts")
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
                        return ToolResultHelper.json(jsonMapper,
                                emailService.searchMessages(principal.getUserSession(), accountId, query, limit));
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
