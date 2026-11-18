package com.yoxel.aurinko.mcp.tools;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ConnectAccountSchemaValidator implements JsonSchemaValidator {

    private final JsonSchemaValidator delegate;
    private final Map<String, Object> connectSchema;

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object value) {
        if (connectSchema.equals(schema) && value instanceof Map<?, ?> arguments
                && arguments.get("serviceType") instanceof String serviceType) {
            var properties = (Map<?, ?>) connectSchema.get("properties");
            var parameter = (Map<?, ?>) properties.get("serviceType");
            var types = (List<?>) parameter.get("enum");
            var canonical = types.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(type -> type.equalsIgnoreCase(serviceType)).findFirst();
            if (canonical.isPresent()) {
                Map<Object, Object> normalized = new HashMap<>(arguments);
                normalized.put("serviceType", canonical.get());
                value = normalized;
            }
        }
        return delegate.validate(schema, value);
    }

    @Override
    public ValidationResponse validateSchema(Map<String, Object> schema) {
        return delegate.validateSchema(schema);
    }
}
