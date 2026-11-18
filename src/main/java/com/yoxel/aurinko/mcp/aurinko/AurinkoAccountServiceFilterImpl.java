package com.yoxel.aurinko.mcp.aurinko;

import java.util.List;

public class AurinkoAccountServiceFilterImpl implements AurinkoAccountService {

    private final AurinkoAccountService delegate;
    private final List<String> allowedServiceTypes;

    public AurinkoAccountServiceFilterImpl(AurinkoAccountService delegate, List<String> allowedServiceTypes) {
        this.delegate = delegate;
        this.allowedServiceTypes = List.copyOf(allowedServiceTypes);
    }

    @Override
    public List<AurinkoAccount> listAccounts(String userId) throws AurinkoException {
        return delegate.listAccounts(userId).stream()
                .filter(account -> account.getServiceType() != null && allowedServiceTypes.stream()
                        .anyMatch(type -> type.equalsIgnoreCase(account.getServiceType())))
                .toList();
    }

    @Override
    public void deleteAccount(String userId, long accountId) throws AurinkoException {
        delegate.deleteAccount(userId, accountId);
    }
}
