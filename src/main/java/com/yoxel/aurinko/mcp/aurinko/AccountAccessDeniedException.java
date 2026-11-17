package com.yoxel.aurinko.mcp.aurinko;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException() {
        super("Account not found");
    }
}
