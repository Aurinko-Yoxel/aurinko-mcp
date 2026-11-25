package com.yoxel.aurinko.mcp.paging;

public class InvalidPageCursorException extends RuntimeException {

    public InvalidPageCursorException() {
        super("Invalid or expired cursor");
    }
}
