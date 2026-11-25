package com.yoxel.aurinko.mcp.tools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class EmailAddressValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"alex@example.com", "Alex.Test+tag@EXAMPLE.CO.UK", "name@sub-domain.example.com"})
    void validEmailAccepted(String email) {
        assertThat(EmailAddressValidator.isValid(email)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-email", "a@", "@example.com", "a..b@example.com", "a@-example.com", "a@localhost",
            "Name <a@example.com>", "a@example.com\r\nBcc:other@example.com", "a@example.com "})
    void invalidEmailRejected(String email) {
        assertThat(EmailAddressValidator.isValid(email)).isFalse();
    }
}
