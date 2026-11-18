package com.yoxel.aurinko.mcp.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalHostHelperTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://127.0.0.1", "http://[::1]", "http://LOCALHOST:8080"})
    void localHostAccepted(String url) {
        assertThat(LocalHostHelper.isLocal(URI.create(url))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "http://localhost.example.com"})
    void remoteHostRejected(String url) {
        assertThat(LocalHostHelper.isLocal(URI.create(url))).isFalse();
    }
}
