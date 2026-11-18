package com.yoxel.aurinko.mcp.aurinko;

import java.util.List;

public interface AurinkoAccountService {

    List<AurinkoAccount> listAccounts(String userId) throws AurinkoException;

    void deleteAccount(String userId, long accountId) throws AurinkoException;
}
