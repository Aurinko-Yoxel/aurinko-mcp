package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.apis.ListSupport_OffsetBased;
import com.yoxel.aurinko.apis.QueryParams;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class AurinkoAccountServiceImpl implements AurinkoAccountService {

    private static final int MAX_ACCOUNTS = 200;

    private final AurinkoService service;

    @Override
    public void deleteAccount(String userId, long accountId) throws AurinkoException {
        try {
            service.users.accounts(userId).delete(accountId);
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }

    @Override
    public List<AurinkoAccount> listAccounts(String userId) throws AurinkoException {
        try {
            var api = service.users.accounts(userId);
            List<AurinkoAccount> accounts = new ArrayList<>();
            int offset = 0;
            while (accounts.size() < MAX_ACCOUNTS) {
                int pageSize = Math.min(ListSupport_OffsetBased.defaultPageSize, MAX_ACCOUNTS - accounts.size());
                var page = api.loadPage(pageSize, offset, QueryParams.EMPTY);
                if (page.getRecords() == null || page.getRecords().length == 0) {
                    break;
                }
                for (var account : page.getRecords()) {
                    accounts.add(new AurinkoAccount(account.getId(), account.getServiceType(),
                            account.getEmail(), account.isActive()));
                    if (accounts.size() == MAX_ACCOUNTS) {
                        return accounts;
                    }
                }
                if (page.isDone()) {
                    break;
                }
                offset = page.getOffset() + pageSize;
            }
            return accounts;
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }
}
