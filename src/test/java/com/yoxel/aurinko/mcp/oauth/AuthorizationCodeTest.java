package com.yoxel.aurinko.mcp.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationCodeTest {

    @Test
    void userSessionExcludedFromString() {
        var code = new AuthorizationCode("user-1", "private-user-session", "client-1", "https://example.com/cb",
                "challenge", "https://mcp.example.com/mcp");

        assertThat(code.toString()).doesNotContain("private-user-session");
    }
}
