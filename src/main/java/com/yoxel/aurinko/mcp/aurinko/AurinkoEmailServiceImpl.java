package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.apis.QueryParams;
import com.yoxel.aurinko.bean.AurEmail;
import com.yoxel.aurinko.bean.sub.EmailAddress;
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
    public List<AurinkoEmailSummary> searchMessages(String userSession, long accountId, String query, int limit)
            throws AurinkoException {
        try (var service = clientFactory.create(userSession, accountId)) {
            QueryParams parameters = query == null || query.isBlank() ? QueryParams.EMPTY : QueryParams.of("q", query);
            var records = service.emails.loadPage(parameters).getRecords();
            return records == null ? List.of() : Arrays.stream(records).limit(limit).map(this::summary).toList();
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
