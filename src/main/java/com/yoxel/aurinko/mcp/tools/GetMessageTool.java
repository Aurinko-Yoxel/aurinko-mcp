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
public class GetMessageTool implements McpTool {

    private final AccountAccessValidator accessValidator;
    private final AurinkoEmailService emailService;
    private final McpJsonMapper jsonMapper;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("get_message", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("accountId", "messageId"),
                                "properties", Map.of("accountId", Map.of("type", "integer"),
                                        "messageId", Map.of("type", "string", "minLength", 1))))
                        .description("Read one email message by id")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).build())
                        .build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        var arguments = new ToolArguments(request.arguments());
                        long accountId = arguments.requireAccountId();
                        String messageId = arguments.requireString("messageId");
                        accessValidator.validate(principal.getUserId(), accountId);
                        return ToolResultHelper.json(jsonMapper,
                                emailService.getMessage(principal.getUserSession(), accountId, messageId));
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
