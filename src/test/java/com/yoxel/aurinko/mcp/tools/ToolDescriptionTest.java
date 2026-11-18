package com.yoxel.aurinko.mcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ToolDescriptionTest {

    @Test
    void parameterDescriptionRequired() {
        var description = new ToolDescription(true, "Tool help", Map.of("id", "Identifier"));
        assertThat(description.parameter("id")).isEqualTo("Identifier");
        assertThatThrownBy(() -> description.parameter("missing")).isInstanceOf(IllegalStateException.class);
    }
}
