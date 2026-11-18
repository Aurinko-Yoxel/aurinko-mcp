package com.yoxel.aurinko.mcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ToolDescriptionsTest {

    @Test
    void toolDescriptionRequired() {
        var tool = new ToolDescription(true, "Tool help", Map.of());
        var descriptions = new ToolDescriptions(Map.of("ping", tool), Map.of());
        assertThat(descriptions.tool("ping")).isEqualTo(tool);
        assertThatThrownBy(() -> descriptions.tool("missing")).isInstanceOf(IllegalStateException.class);
    }
}
