package com.yoxel.aurinko.mcp.aurinko;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceTypeCatalogTest {

    private final ServiceTypeCatalog catalog = new ServiceTypeCatalog(Map.of("Office365", "Custom Microsoft description"));

    @Test
    void knownProviderUsesSuppliedDescription() {
        assertThat(catalog.describe("Office365")).isEqualTo("Custom Microsoft description");
    }

    @Test
    void unknownProviderUsesGenericDescription() {
        assertThat(catalog.describe("Salesforce")).isEqualTo("Aurinko service type Salesforce");
    }

    @Test
    void providerLookupIgnoresCase() {
        assertThat(catalog.describe("office365")).isEqualTo("Custom Microsoft description");
    }
}
