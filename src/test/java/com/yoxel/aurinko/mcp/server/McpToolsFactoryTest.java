package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.support.FakeAurinkoServer;
import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import com.yoxel.aurinko.mcp.tools.ToolDescriptionsLoaderYamlImpl;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class McpToolsFactoryTest {

    @Test
    void registeredToolsAndAllParametersUseYamlDescriptions() throws Exception {
        var descriptions = new ToolDescriptionsLoaderYamlImpl().load(null);
        var config = new ServerConfig("127.0.0.1", 0, "https://mcp.example.com",
                Base64.getEncoder().encodeToString(new byte[32]));
        try (var fake = new FakeAurinkoServer();
                var service = AurinkoService.createWithAppAuth(fake.getUrl(), "client", "secret")) {
            var aurinkoConfig = fake.config();
            var components = AurinkoComponents.create(aurinkoConfig, service, (ListOnlyAccountService) userId -> List.of());
            var oauth = OAuthComponents.create(config, aurinkoConfig, Clock.systemUTC());
            var tools = McpToolsFactory.create(components, oauth, config, aurinkoConfig, McpJsonDefaults.getMapper(), descriptions);
            assertThat(tools).hasSize(descriptions.getTools().size());
            for (var tool : tools) {
                var specification = tool.specification().tool();
                var description = descriptions.tool(specification.name());
                if ("connect_account".equals(specification.name())) {
                    assertThat(specification.description()).startsWith(description.getDescription() + "\n")
                            .endsWith(description.parameter("serviceType"));
                    for (String type : aurinkoConfig.getAccountServiceTypes()) {
                        assertThat(specification.description()).contains(type + ": " + descriptions.getServiceTypes().get(type));
                    }
                } else {
                    assertThat(specification.description()).isEqualTo(description.getDescription());
                }
                var properties = (Map<?, ?>) specification.inputSchema().get("properties");
                assertThat(properties.keySet()).isEqualTo(description.getParameters().keySet());
                properties.forEach((name, value) -> {
                    var parameter = (Map<?, ?>) value;
                    assertThat(parameter.get("description")).isEqualTo(description.parameter((String) name));
                    assertThat(parameter.get("description").toString()).isNotBlank();
                });
            }
        }
    }
}
