package com.yoxel.aurinko.mcp.tools;

import java.nio.file.Path;

public interface ToolDescriptionsLoader {

    ToolDescriptions load(Path overrideFile);
}
