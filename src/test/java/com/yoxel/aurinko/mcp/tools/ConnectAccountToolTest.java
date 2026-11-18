package com.yoxel.aurinko.mcp.tools;

import com.yoxel.aurinko.mcp.accounts.ConnectStateManagerJweImpl;
import com.yoxel.aurinko.mcp.auth.BearerAuthFilter;
import com.yoxel.aurinko.mcp.auth.McpPrincipal;
import com.yoxel.aurinko.mcp.config.ServerConfig;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectAccountToolTest {

    private final ServerConfig config = new ServerConfig("127.0.0.1", 8080, "https://mcp.example.com/",
            Base64.getEncoder().encodeToString(new byte[32]));
    private final ConnectStateManagerJweImpl manager = new ConnectStateManagerJweImpl(
            new JweHelper(config.getTokenKey(), Clock.systemUTC()));
    private final McpPrincipal principal = new McpPrincipal("token-user", "private-session", "client-1");

    @ParameterizedTest
    @ValueSource(strings = {"IMAP", "CalDav"})
    void linkContainsAuthenticatedUserState(String serviceType) throws Exception {
        var result = call(Map.of("serviceType", serviceType, "userId", "untrusted-user"));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        var value = McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {});
        assertThat(((Number) value.get("expiresInSeconds")).longValue()).isEqualTo(600);
        String url = (String) value.get("url");
        assertThat(url).startsWith(config.getPublicUrl() + "/accounts/connect?state=");
        var state = manager.parseState(URLDecoder.decode(URI.create(url).getRawQuery().substring("state=".length()),
                StandardCharsets.UTF_8));
        assertThat(state.getUserId()).isEqualTo(principal.getUserId());
        assertThat(state.getServiceType()).isEqualTo(serviceType);
        assertThat(text).doesNotContain(principal.getUserSession());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Salesforce", "unknown"})
    void unsupportedServiceTypeRejected(String serviceType) {
        var result = call(Map.of("serviceType", serviceType));

        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("Google, Office365, iCloud, IMAP, CalDav");
    }

    @Test
    void lowercaseTypeUsesConfiguredCanonicalName() throws Exception {
        var result = call(Map.of("serviceType", "icloud"));
        assertThat(result.isError()).isFalse();
        var value = McpJsonDefaults.getMapper().readValue(((McpSchema.TextContent) result.content().get(0)).text(),
                new TypeRef<Map<String, Object>>() {});
        String url = (String) value.get("url");
        assertThat(manager.parseState(URLDecoder.decode(URI.create(url).getRawQuery().substring(6),
                StandardCharsets.UTF_8)).getServiceType()).isEqualTo("iCloud");
    }

    @Test
    void schemaAndDescriptionUseConfiguredTypes() {
        var specification = new ConnectAccountTool(manager, config, McpJsonDefaults.getMapper(),
                List.of("Office365", "iCloud", "Salesforce")).specification();
        var properties = (Map<?, ?>) specification.tool().inputSchema().get("properties");
        var parameter = (Map<?, ?>) properties.get("serviceType");
        assertThat(parameter.get("enum")).isEqualTo(List.of("Office365", "iCloud", "Salesforce"));
        assertThat(specification.tool().description()).contains("Office365:", "Outlook.com", "iCloud:", "@icloud.com",
                "Salesforce: Aurinko service type Salesforce", "Choose serviceType by the provider", "Use IMAP or CalDav only");
        assertThat(parameter.get("description").toString()).contains("Choose serviceType by the provider", "Use IMAP or CalDav only");
    }

    @Test
    void imapAndCaldavConfigurationRestrictsOtherProviders() {
        var tool = new ConnectAccountTool(manager, config, McpJsonDefaults.getMapper(), List.of("IMAP", "CalDav"));
        var result = tool.specification().callHandler().apply(exchange(),
                McpSchema.CallToolRequest.builder("connect_account").arguments(Map.of("serviceType", "Office365")).build());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsExactly(new McpSchema.TextContent("serviceType must be one of: IMAP, CalDav"));
        for (String type : List.of("IMAP", "CalDav")) {
            assertThat(tool.specification().callHandler().apply(exchange(), McpSchema.CallToolRequest.builder("connect_account")
                    .arguments(Map.of("serviceType", type)).build()).isError()).isFalse();
        }
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        return new ConnectAccountTool(manager, config, McpJsonDefaults.getMapper(),
                List.of("Google", "Office365", "iCloud", "IMAP", "CalDav")).specification().callHandler()
                .apply(exchange(), McpSchema.CallToolRequest.builder("connect_account").arguments(arguments).build());
    }
    private McpSyncServerExchange exchange() {
        var context = McpTransportContext.create(Map.of(BearerAuthFilter.PRINCIPAL_ATTRIBUTE, principal));
        return new McpSyncServerExchange(new McpAsyncServerExchange("session", null, null, null, context));
    }

}
