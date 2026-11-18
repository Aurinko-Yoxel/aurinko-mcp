package com.yoxel.aurinko.mcp.tools;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ToolDescriptionsLoaderYamlImpl implements ToolDescriptionsLoader {

    private static final int MAX_KEY_LENGTH = 100;

    private final YAMLMapper mapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Override
    public ToolDescriptions load(Path overrideFile) {
        Map<?, ?> defaults;
        try (var stream = getClass().getResourceAsStream("/tools.yaml")) {
            if (stream == null) {
                throw new IllegalArgumentException("Default tools.yaml was not found");
            }
            defaults = read(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read default tools.yaml");
        }
        Map<String, ToolDescription> tools = new LinkedHashMap<>();
        mapping(defaults.get("tools")).forEach((name, value) -> {
            var fields = mapping(value);
            String toolName = key(name);
            tools.put(toolName, new ToolDescription(enabled(fields.get("enabled"), toolName),
                    text(fields.get("description"), display(toolName)), descriptions(fields.get("parameters"), display(toolName))));
        });
        Map<String, String> serviceTypes = new LinkedHashMap<>(descriptions(defaults.get("serviceTypes"), "serviceTypes"));
        if (overrideFile != null) {
            Map<?, ?> overrides;
            if (!Files.isRegularFile(overrideFile) || !Files.isReadable(overrideFile)) {
                throw new IllegalArgumentException("TOOLS_CONFIG_FILE was not found or is not readable");
            }
            try (var stream = Files.newInputStream(overrideFile)) {
                overrides = read(stream);
            } catch (IOException | SecurityException e) {
                throw new IllegalArgumentException("TOOLS_CONFIG_FILE was not found or is not readable");
            }
            for (Object name : overrides.keySet()) {
                if (!Set.of("tools", "serviceTypes").contains(name)) {
                    throw new IllegalArgumentException("Unknown top-level key in tools configuration: " + display(key(name)));
                }
            }
            if (overrides.containsKey("tools")) {
                mapping(overrides.get("tools")).forEach((name, value) -> {
                    String toolName = key(name);
                    ToolDescription current = tools.get(toolName);
                    if (current == null) {
                        throw new IllegalArgumentException("Unknown tool in tools configuration: " + display(toolName));
                    }
                    var fields = mapping(value);
                    for (Object field : fields.keySet()) {
                        if (!Set.of("enabled", "description", "parameters").contains(field)) {
                            throw new IllegalArgumentException("Unknown tool field in tools configuration: "
                                    + display(toolName) + "." + display(key(field)));
                        }
                    }
                    var parameters = new LinkedHashMap<>(current.getParameters());
                    if (fields.containsKey("parameters")) {
                        mapping(fields.get("parameters")).forEach((parameter, description) -> {
                            String parameterName = key(parameter);
                            String location = display(toolName) + "." + display(parameterName);
                            if (!parameters.containsKey(parameterName)) {
                                throw new IllegalArgumentException("Unknown parameter in tools configuration: " + location);
                            }
                            parameters.put(parameterName, text(description, location));
                        });
                    }
                    tools.put(toolName, new ToolDescription(
                            fields.containsKey("enabled") ? enabled(fields.get("enabled"), toolName) : current.isEnabled(),
                            fields.containsKey("description") ? text(fields.get("description"), display(toolName)) : current.getDescription(),
                            parameters));
                });
            }
            if (overrides.containsKey("serviceTypes")) {
                descriptions(overrides.get("serviceTypes"), "serviceTypes").forEach((name, description) -> {
                    String key = serviceTypes.keySet().stream().filter(name::equalsIgnoreCase).findFirst().orElse(name);
                    serviceTypes.put(key, description);
                });
            }
        }
        return new ToolDescriptions(tools, serviceTypes);
    }

    private Map<?, ?> read(InputStream stream) {
        try {
            return mapping(mapper.readValue(stream, Object.class));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid YAML tools configuration");
        }
    }

    private Map<?, ?> mapping(Object value) {
        if (!(value instanceof Map<?, ?> result)) {
            throw new IllegalArgumentException("Expected a mapping in tools configuration");
        }
        return result;
    }

    private Map<String, String> descriptions(Object value, String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        mapping(value).forEach((name, description) -> {
            String descriptionName = key(name);
            result.put(descriptionName, text(description, prefix + "." + display(descriptionName)));
        });
        return result;
    }

    private String key(Object value) {
        if (!(value instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException("Expected a non-empty key in tools configuration");
        }
        return name;
    }

    private String display(String name) {
        return name.substring(0, Math.min(name.length(), MAX_KEY_LENGTH));
    }

    private String text(Object value, String location) {
        if (!(value instanceof String description) || description.isBlank()) {
            throw new IllegalArgumentException("Description must be a non-empty string in tools configuration: " + location);
        }
        return description;
    }

    private boolean enabled(Object value, String toolName) {
        if (!(value instanceof Boolean enabled)) {
            throw new IllegalArgumentException("Tool enabled must be a boolean in tools configuration: " + display(toolName));
        }
        return enabled;
    }
}
