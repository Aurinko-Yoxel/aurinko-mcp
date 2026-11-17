package com.yoxel.aurinko.mcp.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ToolArgumentsTest {

    @Test
    void integerAccountIdAccepted() {
        assertThat(new ToolArguments(Map.of("accountId", 42)).requireAccountId()).isEqualTo(42);
        assertThat(new ToolArguments(Map.of("accountId", Long.MAX_VALUE)).requireAccountId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void missingAccountIdRejected() {
        assertThatThrownBy(() -> new ToolArguments(null).requireAccountId())
                .isInstanceOf(InvalidArgumentException.class).hasMessage("accountId must be an integer");
    }

    @Test
    void invalidAccountIdTypeFractionAndOverflowRejected() {
        for (Object value : new Object[]{"42", 1.5, new BigInteger("9223372036854775808")}) {
            assertThatThrownBy(() -> new ToolArguments(Map.of("accountId", value)).requireAccountId())
                    .isInstanceOf(InvalidArgumentException.class).hasMessage("accountId must be an integer");
        }
    }

    @Test
    void requiredStringPreserved() {
        assertThat(new ToolArguments(Map.of("subject", " Event ")).requireString("subject")).isEqualTo(" Event ");
    }

    @Test
    void missingAndNonStringRequiredValuesRejected() {
        assertThatThrownBy(() -> new ToolArguments(Map.of()).requireString("subject"))
                .isInstanceOf(InvalidArgumentException.class).hasMessageContaining("subject");
        assertThatThrownBy(() -> new ToolArguments(Map.of("subject", 42)).requireString("subject"))
                .isInstanceOf(InvalidArgumentException.class).hasMessageContaining("subject");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankRequiredStringRejected(String value) {
        assertThatThrownBy(() -> new ToolArguments(Map.of("subject", value)).requireString("subject"))
                .isInstanceOf(InvalidArgumentException.class).hasMessage("subject must not be blank");
    }

    @Test
    void optionalStringPreservedOrAbsent() {
        assertThat(new ToolArguments(Map.of("query", "from:alice@example.com")).optionalString("query"))
                .isEqualTo("from:alice@example.com");
        assertThat(new ToolArguments(Map.of("query", "")).optionalString("query")).isEmpty();
        assertThat(new ToolArguments(Map.of()).optionalString("query")).isNull();
    }

    @Test
    void optionalStringWrongTypeRejected() {
        assertThatThrownBy(() -> new ToolArguments(Map.of("query", true)).optionalString("query"))
                .isInstanceOf(InvalidArgumentException.class).hasMessage("query must be a string");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 50})
    void optionalIntegerWithinBoundsAccepted(int value) {
        assertThat(new ToolArguments(Map.of("limit", value)).optionalInt("limit", 20, 1, 50)).isEqualTo(value);
    }

    @Test
    void optionalIntegerAbsentUsesDefault() {
        assertThat(new ToolArguments(Map.of()).optionalInt("limit", 20, 1, 50)).isEqualTo(20);
    }

    @Test
    void optionalIntegerWrongTypeFractionOverflowAndBoundsRejected() {
        for (Object value : new Object[]{"20", 1.5, 0, 51, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> new ToolArguments(Map.of("limit", value)).optionalInt("limit", 20, 1, 50))
                    .isInstanceOf(InvalidArgumentException.class).hasMessage("limit must be an integer between 1 and 50");
        }
    }

    @Test
    void dateTimeWithOffsetAccepted() {
        String value = "2026-11-17T09:00:00+02:00";
        assertThat(new ToolArguments(Map.of("timeMin", value)).requireDateTime("timeMin"))
                .isEqualTo(OffsetDateTime.parse(value));
    }

    @Test
    void missingAndWrongTypeDateTimeRejected() {
        assertThatThrownBy(() -> new ToolArguments(Map.of()).requireDateTime("timeMin"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("timeMin must be an ISO-8601 date-time with offset");
        assertThatThrownBy(() -> new ToolArguments(Map.of("timeMin", 123)).requireDateTime("timeMin"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("timeMin must be an ISO-8601 date-time with offset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-17T09:00:00", "2026-11-17", "not-a-date", ""})
    void dateTimeWithoutOffsetOrInvalidRejected(String value) {
        assertThatThrownBy(() -> new ToolArguments(Map.of("timeMin", value)).requireDateTime("timeMin"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("timeMin must be an ISO-8601 date-time with offset");
    }
}
