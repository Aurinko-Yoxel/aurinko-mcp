package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.paging.PageCursor;
import com.yoxel.aurinko.mcp.paging.PageResult;

public interface AurinkoEmailService {

    PageResult<AurinkoEmailSummary> searchMessages(String userSession, long accountId, String query, PageCursor start, int limit)
            throws AurinkoException;

    String sendMessage(String userSession, long accountId, AurinkoEmailDraft draft) throws AurinkoException;

    AurinkoEmail getMessage(String userSession, long accountId, String messageId) throws AurinkoException;
}
