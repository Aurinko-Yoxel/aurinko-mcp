package com.yoxel.aurinko.mcp.aurinko;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceTypeCatalogTest {

    @Test
    void knownProviderDescribed() {
        assertThat(ServiceTypeCatalog.describe("Office365")).contains("Microsoft 365", "Outlook.com", "Microsoft");
    }

    @Test
    void unknownProviderUsesGenericDescription() {
        assertThat(ServiceTypeCatalog.describe("Salesforce")).isEqualTo("Aurinko service type Salesforce");
    }
}
