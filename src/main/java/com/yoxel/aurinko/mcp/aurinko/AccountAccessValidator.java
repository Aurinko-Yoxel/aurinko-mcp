package com.yoxel.aurinko.mcp.aurinko;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountAccessValidator {

    private final AurinkoAccountService accountService;

    public void validate(String userId, long accountId) throws AccountAccessDeniedException, AurinkoException {
        validate(userId, accountId, true);
    }

    public void validateOwnership(String userId, long accountId) throws AccountAccessDeniedException, AurinkoException {
        validate(userId, accountId, false);
    }

    private void validate(String userId, long accountId, boolean requireActive) {
        boolean accessible = accountService.listAccounts(userId).stream()
                .anyMatch(account -> account.getId() == accountId && (!requireActive || account.isActive()));
        if (!accessible) {
            throw new AccountAccessDeniedException();
        }
    }
}
