package com.yoxel.aurinko.mcp.oauth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class RedirectUriValidatorTest {

    private final RedirectUriValidator validator = new DefaultRedirectUriValidator();

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/cb", "http://localhost:3000/cb", "http://127.0.0.1/cb"})
    void allowedRedirectUriAccepted(String uri) {
        assertThat(validator.isValid(uri)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"http://example.com/cb", "https://example.com/cb#fragment", "not-a-url",
            "http://localhost.example.com/cb", "javascript:alert(1)", "https://example.com/cb#"})
    void invalidRedirectUriRejected(String uri) {
        assertThat(validator.isValid(uri)).isFalse();
    }
}
