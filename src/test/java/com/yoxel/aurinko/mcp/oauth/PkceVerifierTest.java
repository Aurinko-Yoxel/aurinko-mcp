package com.yoxel.aurinko.mcp.oauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class PkceVerifierTest {

    private final PkceVerifier verifier = new PkceVerifierS256Impl();
    private final String challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void rfcVerifierMatchesChallenge() {
        assertThat(verifier.verify("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", challenge)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXj", "short",
            "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjX!"})
    void invalidVerifierRejected(String value) {
        assertThat(verifier.verify(value, challenge)).isFalse();
    }

    @Test
    void oversizedVerifierRejected() {
        assertThat(verifier.verify("a".repeat(129), "wSywJKLlVRzKDgj86PHF4xRVXMP-9jKe6ZSj23UhZq4")).isFalse();
    }

    @Test
    void undersizedVerifierRejectedDespiteMatchingHash() {
        assertThat(verifier.verify("a".repeat(42), "elOGB_2quSlplZKfRRVlu7gULhhEEXMiqv0rPXawGv8")).isFalse();
    }

    @Test
    void invalidCharacterRejectedDespiteMatchingHash() {
        assertThat(verifier.verify("a".repeat(42) + "!", "eejtYKWJY_EVRpWyQ5uVYYEekHJHZ8_ubIlUxhzqIMA")).isFalse();
    }
}
