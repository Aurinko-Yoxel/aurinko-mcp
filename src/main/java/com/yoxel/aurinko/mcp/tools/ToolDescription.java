package com.yoxel.aurinko.mcp.tools;

import lombok.Value;

import java.util.Map;

@Value
public class ToolDescription {

    boolean enabled;
    String description;
    Map<String, String> parameters;

    public ToolDescription(boolean enabled, String description, Map<String, String> parameters) {
        this.enabled = enabled;
        this.description = description;
        this.parameters = Map.copyOf(parameters);
    }

    public String parameter(String name) {
        String value = parameters.get(name);
        if (value == null) {
            throw new IllegalStateException("Tool parameter description is missing: " + name);
        }
        return value;
    }
}
