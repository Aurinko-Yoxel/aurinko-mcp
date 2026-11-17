package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UserSignatureHelperTest {

    @Test
    void base64UrlSecretProducesKnownHmacSignature() {
        var config = new AurinkoConfig("https://api.example.com", "client", "_-7dzLuqmYh3ZlVEMyIRAA",
                List.of("Google"), null);

        String signature = new UserSignatureHelper(config).sign("user-1", 1800000000L);

        assertThat(signature).isEqualTo("f8fa1d83f45f4a9eb377dab9727b2e6fe6a671a425b303be48243e4d763523fd");
    }
}
