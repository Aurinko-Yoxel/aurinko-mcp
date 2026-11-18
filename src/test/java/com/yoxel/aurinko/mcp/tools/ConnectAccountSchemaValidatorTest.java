package com.yoxel.aurinko.mcp.tools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectAccountSchemaValidatorTest {

    private final Map<String, Object> schema = Map.of("type", "object", "required", List.of("serviceType"),
            "additionalProperties", false, "properties", Map.of("serviceType",
                    Map.of("type", "string", "enum", List.of("iCloud", "IMAP"))));
    private final ConnectAccountSchemaValidator validator = new ConnectAccountSchemaValidator(
            McpJsonDefaults.getSchemaValidator(), schema);

    @Test
    void matchingTypeValidatedWithoutChangingArguments() {
        var arguments = Map.of("serviceType", "icloud");
        assertThat(validator.validate(schema, arguments).valid()).isTrue();
        assertThat(arguments).containsEntry("serviceType", "icloud");
        assertThat(validator.validateSchema(schema).valid()).isTrue();
    }

    @Test
    void invalidArgumentsRemainRejected() {
        assertThat(validator.validate(schema, Map.of("serviceType", "Salesforce")).valid()).isFalse();
        assertThat(validator.validate(schema, Map.of()).valid()).isFalse();
        assertThat(validator.validate(schema, Map.of("serviceType", "icloud", "extra", true)).valid()).isFalse();
    }

    @Test
    void otherSchemasRemainCaseSensitive() {
        Map<String, Object> other = Map.of("type", "object", "properties", Map.of("value",
                Map.of("type", "string", "enum", List.of("Example"))));
        assertThat(validator.validate(other, Map.of("value", "example")).valid()).isFalse();
    }
}
