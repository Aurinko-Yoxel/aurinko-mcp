package com.yoxel.aurinko.mcp.aurinko;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountAccessValidator {

    private final AurinkoAccountService accountService;

    public void validate(String userId, long accountId) throws AccountAccessDeniedException, AurinkoException {
        boolean accessible = accountService.listAccounts(userId).stream()
                .anyMatch(account -> account.getId() == accountId && account.isActive());
        if (!accessible) {
            throw new AccountAccessDeniedException();
        }
    }
}
