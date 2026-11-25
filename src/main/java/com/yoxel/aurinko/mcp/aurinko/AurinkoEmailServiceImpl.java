package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.apis.QueryParams;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.json.JsonHttpContent;
import com.yoxel.aurinko.bean.AurEmailSendResponse;
import com.yoxel.aurinko.bean.AurEmail;
import com.yoxel.aurinko.bean.sub.EmailAddress;
import com.yoxel.aurinko.mcp.paging.PageCursor;
import com.yoxel.aurinko.mcp.paging.PageResult;
import com.yoxel.aurinko.mcp.paging.PageTraversal;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class AurinkoEmailServiceImpl implements AurinkoEmailService {

    private static final int MAX_BODY_LENGTH = 50_000;

    private final AurinkoClientFactory clientFactory;

    @Override
    public PageResult<AurinkoEmailSummary> searchMessages(String userSession, long accountId, String query, PageCursor start, int limit)
            throws AurinkoException {
        try (var service = clientFactory.create(userSession, accountId)) {
            QueryParams parameters = query == null || query.isBlank() ? QueryParams.EMPTY : QueryParams.of("q", query);
            return PageTraversal.load(start, limit, token -> {
                var page = service.emails.loadPage(parameters.add("pageToken", token));
                var records = page.getRecords();
                return new PageTraversal.Page<>(records == null ? List.of()
                        : Arrays.stream(records).map(this::summary).toList(), page.getNextPageToken());
            });
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }

    @Override
    public AurinkoEmail getMessage(String userSession, long accountId, String messageId) throws AurinkoException {
        try (var service = clientFactory.create(userSession, accountId)) {
            var message = service.emails.read(URLEncoder.encode(messageId, StandardCharsets.UTF_8).replace("+", "%20"));
            var summary = summary(message);
            String body = message.getBody();
            boolean truncated = body != null && body.length() > MAX_BODY_LENGTH;
            return new AurinkoEmail(summary.getId(), summary.getThreadId(), summary.getSubject(), summary.getFrom(),
                    summary.getTo(), summary.getReceivedAt(), summary.isHasAttachments(), addresses(message.getCc()),
                    truncated ? body.substring(0, MAX_BODY_LENGTH) : body, truncated);
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }

    @Override
    public String sendMessage(String userSession, long accountId, AurinkoEmailDraft draft) throws AurinkoException {
        try (var service = clientFactory.create(userSession, accountId)) {
            var message = new AurEmail();
            message.setTo(draft.getTo().stream().map(this::recipient).toList());
            message.setCc(draft.getCc().stream().map(this::recipient).toList());
            message.setSubject(draft.getSubject());
            message.setBody(draft.getBody());
            var request = service.emails.httpPostPrepare(service.emails.entityPath(),
                    QueryParams.of(QueryParams.qp("bodyType", "text"), QueryParams.qp("returnIds", true)),
                    new JsonHttpContent(Utils.getDefaultJsonFactory(), message));
            request.setNumberOfRetries(0);
            request.setIOExceptionHandler(null);
            var response = service.emails.httpExecute(request);
            try {
                var result = response.parseAs(AurEmailSendResponse.class);
                if (result == null || result.getId() == null || result.getId().isBlank()) {
                    throw new AurinkoException();
                }
                return result.getId();
            } finally {
                response.disconnect();
            }
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }

    private EmailAddress recipient(String email) {
        var address = new EmailAddress();
        address.setAddress(email);
        return address;
    }

    private AurinkoEmailSummary summary(AurEmail message) {
        return new AurinkoEmailSummary(message.getId(), message.getThreadId(), message.getSubject(),
                address(message.getFrom()), addresses(message.getTo()),
                message.getReceivedAt() == null ? null : message.getReceivedAt().toStringRfc3339(),
                message.isHasAttachments());
    }

    private List<String> addresses(List<EmailAddress> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(this::address).toList();
    }

    private String address(EmailAddress value) {
        if (value == null) {
            return null;
        }
        if (value.getAddress() == null || value.getAddress().isBlank()) {
            return value.getRaw();
        }
        return value.getName() == null || value.getName().isBlank() ? value.getAddress()
                : value.getName() + " <" + value.getAddress() + ">";
    }
}
