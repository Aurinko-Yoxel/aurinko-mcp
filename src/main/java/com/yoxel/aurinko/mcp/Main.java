package com.yoxel.aurinko.mcp;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.server.AurinkoComponents;
import com.yoxel.aurinko.mcp.server.HttpRoutes;
import com.yoxel.aurinko.mcp.server.JettyServerFactory;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;

import java.util.Locale;

@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        useEnglishMessages();
        ServerConfig config = ServerConfig.fromEnvironment();
        AurinkoConfig aurinkoConfig = AurinkoConfig.fromEnvironment();
        var aurinko = AurinkoComponents.create(aurinkoConfig);
        Server server = JettyServerFactory.create(config, aurinkoConfig, aurinko);
        server.setStopAtShutdown(true);
        try {
            server.start();
            log.info("Aurinko MCP server listening on {}:{} at {}", config.getHost(), config.getPort(), HttpRoutes.MCP_PATH);
            server.join();
        } finally {
            server.stop();
        }
    }

    public static void useEnglishMessages() {
        Locale.setDefault(Locale.ENGLISH);
    }
}
