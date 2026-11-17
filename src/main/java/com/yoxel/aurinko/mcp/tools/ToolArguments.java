package com.yoxel.aurinko.mcp.tools;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

public class ToolArguments {

    private final Map<String, Object> values;

    public ToolArguments(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    public long requireAccountId() {
        try {
            if (values.get("accountId") instanceof Number number) {
                return new BigDecimal(number.toString()).longValueExact();
            }
        } catch (ArithmeticException | NumberFormatException e) {
            throw new InvalidArgumentException("accountId must be an integer");
        }
        throw new InvalidArgumentException("accountId must be an integer");
    }

    public String requireString(String name) {
        if (values.get(name) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new InvalidArgumentException(name + " must not be blank");
    }

    public String optionalString(String name) {
        if (!values.containsKey(name)) {
            return null;
        }
        if (values.get(name) instanceof String value) {
            return value;
        }
        throw new InvalidArgumentException(name + " must be a string");
    }

    public int optionalInt(String name, int defaultValue, int min, int max) {
        if (!values.containsKey(name)) {
            return defaultValue;
        }
        String message = name + " must be an integer between " + min + " and " + max;
        try {
            if (values.get(name) instanceof Number number) {
                int value = new BigDecimal(number.toString()).intValueExact();
                if (value >= min && value <= max) {
                    return value;
                }
            }
        } catch (ArithmeticException | NumberFormatException e) {
            throw new InvalidArgumentException(message);
        }
        throw new InvalidArgumentException(message);
    }

    public OffsetDateTime requireDateTime(String name) {
        String message = name + " must be an ISO-8601 date-time with offset";
        if (values.get(name) instanceof String value) {
            try {
                return OffsetDateTime.parse(value);
            } catch (DateTimeParseException e) {
                throw new InvalidArgumentException(message);
            }
        }
        throw new InvalidArgumentException(message);
    }
}
