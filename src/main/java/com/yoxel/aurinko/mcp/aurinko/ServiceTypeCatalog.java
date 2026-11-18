package com.yoxel.aurinko.mcp.aurinko;

import java.util.Map;

public class ServiceTypeCatalog {

    private final Map<String, String> descriptions;

    public ServiceTypeCatalog(Map<String, String> descriptions) {
        this.descriptions = Map.copyOf(descriptions);
    }

    public String describe(String serviceType) {
        return descriptions.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(serviceType))
                .map(Map.Entry::getValue).findFirst().orElse("Aurinko service type " + serviceType);
    }
}
