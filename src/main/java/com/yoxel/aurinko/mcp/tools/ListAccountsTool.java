package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ListAccountsTool implements McpTool {

    private final AurinkoAccountService accountService;
    private final McpJsonMapper jsonMapper;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("list_accounts", Map.of(
                                "type", "object", "properties", Map.of(), "additionalProperties", false))
                        .description("List mailboxes and calendars connected by the current user")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).build())
                        .build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        var accounts = accountService.listAccounts(principal.getUserId());
                        return ToolResultHelper.json(jsonMapper, accounts);
                    } catch (AurinkoException | IOException e) {
                        ToolErrorLogger.log(log, "Failed to load accounts from Aurinko", e);
                        return ToolResultHelper.error("Failed to load accounts from Aurinko");
                    }
                })
                .build();
    }
}
