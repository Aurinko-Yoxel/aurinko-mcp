package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.support.FakeEmailServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AurinkoClientFactoryImplTest {

    @Test
    void userSessionAndAccountHeadersSentWithoutAppAuthorization() throws Exception {
        try (var fake = new FakeEmailServer()) {
            var factory = new AurinkoClientFactoryImpl(fake.config());
            try (var client = factory.create("private-user-session", 42)) {
                client.emails.loadPage();
            }

            assertThat(fake.getHeader("X-Aurinko-Session")).isEqualTo("private-user-session");
            assertThat(fake.getHeader("X-Aurinko-Account-Id")).isEqualTo("42");
            assertThat(fake.getHeader("Authorization")).isNull();
            assertThat(fake.getHeader("User-Agent")).contains("aurinko-mcp");
        }
    }
}
