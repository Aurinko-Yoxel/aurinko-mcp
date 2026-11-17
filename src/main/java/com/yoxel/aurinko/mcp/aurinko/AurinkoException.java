package com.yoxel.aurinko.mcp.aurinko;

public class AurinkoException extends RuntimeException {

    public AurinkoException() {
        super("Aurinko authentication failed");
    }

    public AurinkoException(Throwable cause) {
        super("Aurinko authentication failed", cause);
    }
}
