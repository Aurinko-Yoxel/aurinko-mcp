package com.yoxel.aurinko.mcp.tools;

import lombok.Value;

import java.util.Map;

@Value
public class ToolDescriptions {

    Map<String, ToolDescription> tools;
    Map<String, String> serviceTypes;

    public ToolDescriptions(Map<String, ToolDescription> tools, Map<String, String> serviceTypes) {
        this.tools = Map.copyOf(tools);
        this.serviceTypes = Map.copyOf(serviceTypes);
    }

    public ToolDescription tool(String name) {
        ToolDescription value = tools.get(name);
        if (value == null) {
            throw new IllegalStateException("Tool description is missing: " + name);
        }
        return value;
    }
}
