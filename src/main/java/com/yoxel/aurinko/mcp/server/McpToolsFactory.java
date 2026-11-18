package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.tools.ConnectAccountTool;
import com.yoxel.aurinko.mcp.tools.CreateEventTool;
import com.yoxel.aurinko.mcp.tools.DisconnectAccountTool;
import com.yoxel.aurinko.mcp.tools.GetMessageTool;
import com.yoxel.aurinko.mcp.tools.ListAccountsTool;
import com.yoxel.aurinko.mcp.tools.ListEventsTool;
import com.yoxel.aurinko.mcp.tools.McpTool;
import com.yoxel.aurinko.mcp.tools.PingTool;
import com.yoxel.aurinko.mcp.tools.SearchMessagesTool;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.util.List;

public class McpToolsFactory {

    private McpToolsFactory() {
    }

    public static List<McpTool> create(AurinkoComponents aurinko, OAuthComponents oauth,
            ServerConfig config, AurinkoConfig aurinkoConfig, McpJsonMapper jsonMapper) {
        var validator = aurinko.getAccountAccessValidator();
        return List.of(new PingTool(), new ListAccountsTool(aurinko.getAccountService(), jsonMapper),
                new SearchMessagesTool(validator, aurinko.getEmailService(), jsonMapper),
                new GetMessageTool(validator, aurinko.getEmailService(), jsonMapper),
                new ListEventsTool(validator, aurinko.getCalendarService(), jsonMapper),
                new CreateEventTool(validator, aurinko.getCalendarService(), jsonMapper),
                new ConnectAccountTool(oauth.getConnectStateManager(), config, jsonMapper, aurinkoConfig.getAccountServiceTypes()),
                new DisconnectAccountTool(validator, aurinko.getAccountService(), jsonMapper));
    }
}
