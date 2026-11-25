package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.tools.ToolDescriptionsLoaderYamlImpl;
import io.modelcontextprotocol.json.McpJsonDefaults;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.LifeCycle;

import java.io.IOException;
import java.time.Clock;

@Slf4j
public class JettyServerFactory {

    private JettyServerFactory() {
    }

    public static Server create(ServerConfig config, AurinkoConfig aurinkoConfig, AurinkoComponents aurinko) {
        var descriptions = new ToolDescriptionsLoaderYamlImpl().load(config.getToolsConfigFile());
        var clock = Clock.systemUTC();
        var oauth = OAuthComponents.create(config, aurinkoConfig, clock);
        var jsonMapper = McpJsonDefaults.getMapper();
        var transport = McpServerFactory.createTransport(jsonMapper);
        var tools = McpToolsFactory.create(aurinko, oauth, config, aurinkoConfig, jsonMapper, descriptions);
        var mcpServer = McpServerFactory.create(transport, tools, descriptions.getInstructions());

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(config.getHost());
        connector.setPort(config.getPort());
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath(HttpRoutes.CONTEXT_PATH);
        HttpRoutes.register(context, transport, aurinko, oauth, config, jsonMapper);
        server.setHandler(context);
        server.addEventListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopping(LifeCycle event) {
                try {
                    mcpServer.closeGracefully();
                } finally {
                    try {
                        aurinko.getAurinkoService().close();
                    } catch (IOException e) {
                        log.warn("Unable to close Aurinko client");
                    }
                }
            }
        });
        return server;
    }
}
