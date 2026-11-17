package com.yoxel.aurinko.mcp.aurinko;

import java.util.List;

public interface AurinkoEmailService {

    List<AurinkoEmailSummary> searchMessages(String userSession, long accountId, String query, int limit)
            throws AurinkoException;

    AurinkoEmail getMessage(String userSession, long accountId, String messageId) throws AurinkoException;
}
