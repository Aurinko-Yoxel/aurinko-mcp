package com.yoxel.aurinko.mcp.support;

import com.yoxel.aurinko.mcp.aurinko.ServiceTypeCatalog;
import com.yoxel.aurinko.mcp.tools.ToolDescription;
import com.yoxel.aurinko.mcp.paging.PageCursorManager;
import com.yoxel.aurinko.mcp.paging.PageCursorManagerJweImpl;
import com.yoxel.aurinko.mcp.oauth.JweHelper;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import com.yoxel.aurinko.mcp.tools.ToolDescriptions;
import com.yoxel.aurinko.mcp.tools.ToolDescriptionsLoaderYamlImpl;

public class TestToolDescriptions {

    private static final ToolDescriptions DEFAULTS = new ToolDescriptionsLoaderYamlImpl().load(null);

    public static PageCursorManager cursors() {
        return new PageCursorManagerJweImpl(new JweHelper(new SecretKeySpec(new byte[32], "AES"), Clock.systemUTC()));
    }

    public static ToolDescription tool(String name) {
        return DEFAULTS.tool(name);
    }

    public static ServiceTypeCatalog catalog() {
        return new ServiceTypeCatalog(DEFAULTS.getServiceTypes());
    }
}
