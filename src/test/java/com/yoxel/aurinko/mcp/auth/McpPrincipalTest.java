package com.yoxel.aurinko.mcp.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class McpPrincipalTest {

    @Test
    void userSessionExcludedFromString() {
        var principal = new McpPrincipal("user-1", "private-user-session", "client-1");

        assertThat(principal.toString()).doesNotContain("private-user-session");
    }
}
