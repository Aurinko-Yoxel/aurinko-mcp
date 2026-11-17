package com.yoxel.aurinko.mcp.auth;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class McpPrincipalHelperTest {

    @Test
    void missingPrincipalRejected() {
        var exchange = new McpSyncServerExchange(new McpAsyncServerExchange(
                "session", null, null, null, McpTransportContext.EMPTY));

        assertThatThrownBy(() -> McpPrincipalHelper.fromExchange(exchange)).isInstanceOf(IllegalStateException.class);
    }
}
