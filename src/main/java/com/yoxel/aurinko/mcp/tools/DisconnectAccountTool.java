package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.aurinko.AccountAccessDeniedException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
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
public class DisconnectAccountTool implements McpTool {

    private final AccountAccessValidator accessValidator;
    private final AurinkoAccountService accountService;
    private final McpJsonMapper jsonMapper;
    private final ToolDescription toolDescription;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("disconnect_account", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("accountId"),
                                "properties", Map.of("accountId", Map.of("type", "integer", "description", toolDescription.parameter("accountId")))))
                        .description(toolDescription.getDescription())
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false)
                                .destructiveHint(true).idempotentHint(false).build())
                        .build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        long accountId = new ToolArguments(request.arguments()).requireAccountId();
                        accessValidator.validateOwnership(principal.getUserId(), accountId);
                        accountService.deleteAccount(principal.getUserId(), accountId);
                        return ToolResultHelper.json(jsonMapper, Map.of("accountId", accountId, "disconnected", true));
                    } catch (InvalidArgumentException e) {
                        return ToolResultHelper.error(e.getMessage());
                    } catch (AccountAccessDeniedException e) {
                        return ToolResultHelper.error("Account not found");
                    } catch (AurinkoException e) {
                        if (Integer.valueOf(404).equals(e.getStatusCode())) {
                            return ToolResultHelper.error("This account cannot be disconnected. "
                                    + "The account used to sign in cannot be removed.");
                        }
                        ToolErrorLogger.log(log, "Unable to disconnect the account.", e);
                        return ToolResultHelper.error("Unable to disconnect the account.");
                    } catch (IOException e) {
                        ToolErrorLogger.log(log, "Unable to disconnect the account.", e);
                        return ToolResultHelper.error("Unable to disconnect the account.");
                    }
                })
                .build();
    }
}
