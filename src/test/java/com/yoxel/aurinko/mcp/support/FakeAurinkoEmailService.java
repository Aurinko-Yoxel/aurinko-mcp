package com.yoxel.aurinko.mcp.support;

import com.yoxel.aurinko.mcp.aurinko.AurinkoEmail;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailSummary;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import lombok.Getter;
import lombok.Setter;
import com.yoxel.aurinko.mcp.paging.PageResult;
import com.yoxel.aurinko.mcp.paging.PageCursor;

import java.util.List;

@Getter
public class FakeAurinkoEmailService implements AurinkoEmailService {

    private int calls;
    private PageCursor start;
    @Setter
    private PageCursor next;
    private String userSession;
    private long accountId;
    private String query;
    private int limit;
    private String messageId;
    private AurinkoException failure;

    public void failWith(AurinkoException failure) {
        this.failure = failure;
    }

    @Override
    public PageResult<AurinkoEmailSummary> searchMessages(String userSession, long accountId, String query, PageCursor start, int limit) {
        calls++;
        this.start = start;
        this.userSession = userSession;
        this.accountId = accountId;
        this.query = query;
        this.limit = limit;
        if (failure != null) {
            throw failure;
        }
        return new PageResult<>(List.of(new AurinkoEmailSummary("message-1", "thread-1", "private-subject", "alice@example.com",
                List.of("bob@example.com"), "2026-11-17T10:00:00Z", true)), next);
    }

    @Override
    public AurinkoEmail getMessage(String userSession, long accountId, String messageId) {
        calls++;
        this.userSession = userSession;
        this.accountId = accountId;
        this.messageId = messageId;
        if (failure != null) {
            throw failure;
        }
        return new AurinkoEmail(messageId, "thread-1", "private-subject", "alice@example.com",
                List.of("bob@example.com"), "2026-11-17T10:00:00Z", true,
                List.of("carol@example.com"), "private-body", false);
    }
}
