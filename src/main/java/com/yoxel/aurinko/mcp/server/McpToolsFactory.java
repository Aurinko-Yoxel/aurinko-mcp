package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.tools.ConnectAccountTool;
import com.yoxel.aurinko.mcp.tools.ToolDescriptions;
import com.yoxel.aurinko.mcp.aurinko.ServiceTypeCatalog;
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
            ServerConfig config, AurinkoConfig aurinkoConfig, McpJsonMapper jsonMapper, ToolDescriptions descriptions) {
        var validator = aurinko.getAccountAccessValidator();
        var catalog = new ServiceTypeCatalog(descriptions.getServiceTypes());
        List<McpTool> tools = List.of(new PingTool(descriptions.tool("ping")),
                new ListAccountsTool(aurinko.getAccountService(), jsonMapper, descriptions.tool("list_accounts")),
                new SearchMessagesTool(validator, aurinko.getEmailService(), jsonMapper, descriptions.tool("search_messages")),
                new GetMessageTool(validator, aurinko.getEmailService(), jsonMapper, descriptions.tool("get_message")),
                new ListEventsTool(validator, aurinko.getCalendarService(), jsonMapper, descriptions.tool("list_events")),
                new CreateEventTool(validator, aurinko.getCalendarService(), jsonMapper, descriptions.tool("create_event")),
                new ConnectAccountTool(oauth.getConnectStateManager(), config, jsonMapper, aurinkoConfig.getAccountServiceTypes(),
                        catalog, descriptions.tool("connect_account")),
                new DisconnectAccountTool(validator, aurinko.getAccountService(), jsonMapper, descriptions.tool("disconnect_account")));
        return tools.stream().filter(tool -> descriptions.tool(tool.specification().tool().name()).isEnabled()).toList();
    }
}
