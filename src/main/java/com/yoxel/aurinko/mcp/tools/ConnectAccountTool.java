package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.accounts.ConnectState;
import com.yoxel.aurinko.mcp.accounts.ConnectStateManager;
import com.yoxel.aurinko.mcp.accounts.InvalidConnectStateException;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import com.yoxel.aurinko.mcp.aurinko.ServiceTypeCatalog;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ConnectAccountTool implements McpTool {

    private static final String SELECTION_RULE = "Choose serviceType by the provider of the user's email address. "
            + "Use IMAP or CalDav only when no other listed type matches the provider.";

    private final ConnectStateManager stateManager;
    private final ServerConfig config;
    private final McpJsonMapper jsonMapper;
    private final List<String> allowedServiceTypes;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("connect_account", Map.of(
                                "type", "object", "additionalProperties", false,
                                "required", List.of("serviceType"), "properties", Map.of("serviceType",
                                        Map.of("type", "string", "enum", allowedServiceTypes, "description", SELECTION_RULE))))
                        .description(description())
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).destructiveHint(false).build())
                        .build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        String serviceType = new ToolArguments(request.arguments()).requireString("serviceType");
                        serviceType = canonicalServiceType(serviceType);
                        String state = stateManager.createState(principal.getUserId(), serviceType);
                        String url = config.getPublicUrl() + "/accounts/connect?state="
                                + URLEncoder.encode(state, StandardCharsets.UTF_8);
                        return ToolResultHelper.json(jsonMapper,
                                Map.of("url", url, "expiresInSeconds", ConnectState.LIFETIME.toSeconds()));
                    } catch (InvalidArgumentException e) {
                        return ToolResultHelper.error(e.getMessage());
                    } catch (InvalidConnectStateException | IOException e) {
                        ToolErrorLogger.log(log, "Failed to prepare account connection", e);
                        return ToolResultHelper.error("Failed to prepare account connection");
                    }
                })
                .build();
    }
    private String canonicalServiceType(String value) {
        return allowedServiceTypes.stream().filter(type -> type.equalsIgnoreCase(value)).findFirst()
                .orElseThrow(() -> new InvalidArgumentException("serviceType must be one of: "
                        + String.join(", ", allowedServiceTypes)));
    }

    private String description() {
        String types = allowedServiceTypes.stream().map(type -> type + ": " + ServiceTypeCatalog.describe(type))
                .collect(java.util.stream.Collectors.joining("\n"));
        return "Get a link that the user must open in a browser to connect another mailbox or calendar.\n"
                + types + "\n" + SELECTION_RULE;
    }

}
