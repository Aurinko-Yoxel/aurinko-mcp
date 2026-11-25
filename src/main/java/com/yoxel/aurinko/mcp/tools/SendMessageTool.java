package com.yoxel.aurinko.mcp.tools;

import com.google.api.client.http.HttpResponseException;
import com.yoxel.aurinko.bean.AurHttpError;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessDeniedException;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailDraft;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import com.yoxel.aurinko.mcp.auth.McpPrincipalHelper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SendMessageTool implements McpTool {

    private static final int MAX_RECIPIENTS = 20;
    private static final int MAX_BODY_LENGTH = 50_000;
    private static final String FAILURE = "Failed to send message; it may have been sent, check before retrying";

    private final AccountAccessValidator accessValidator;
    private final AurinkoEmailService emailService;
    private final McpJsonMapper jsonMapper;
    private final ToolDescription toolDescription;

    @Override
    public McpServerFeatures.SyncToolSpecification specification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("send_message", Map.of("type", "object", "additionalProperties", false,
                                "required", List.of("accountId", "to", "subject", "body"), "properties", Map.of(
                                        "accountId", Map.of("type", "integer", "description", toolDescription.parameter("accountId")),
                                        "to", Map.of("type", "array", "minItems", 1, "maxItems", MAX_RECIPIENTS,
                                                "items", Map.of("type", "string", "format", "email"), "description", toolDescription.parameter("to")),
                                        "cc", Map.of("type", "array", "maxItems", MAX_RECIPIENTS,
                                                "items", Map.of("type", "string", "format", "email"), "description", toolDescription.parameter("cc")),
                                        "subject", Map.of("type", "string", "minLength", 1, "description", toolDescription.parameter("subject")),
                                        "body", Map.of("type", "string", "maxLength", MAX_BODY_LENGTH, "description", toolDescription.parameter("body")))))
                        .description(toolDescription.getDescription())
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).destructiveHint(true)
                                .idempotentHint(false).build()).build())
                .callHandler((exchange, request) -> {
                    var principal = McpPrincipalHelper.fromExchange(exchange);
                    try {
                        var arguments = new ToolArguments(request.arguments());
                        long accountId = arguments.requireAccountId();
                        var to = arguments.optionalList("to");
                        var cc = arguments.optionalList("cc");
                        if (to.isEmpty()) {
                            throw new InvalidArgumentException("to must contain at least one recipient");
                        }
                        if (to.size() + cc.size() > MAX_RECIPIENTS) {
                            throw new InvalidArgumentException("Too many recipients");
                        }
                        String subject = arguments.requireString("subject");
                        String body = arguments.requireText("body");
                        if (body.length() > MAX_BODY_LENGTH) {
                            throw new InvalidArgumentException("Message body must not exceed 50000 characters");
                        }
                        var draft = new AurinkoEmailDraft(recipients(to), recipients(cc), subject, body);
                        accessValidator.validate(principal.getUserId(), accountId);
                        return ToolResultHelper.json(jsonMapper,
                                Map.of("id", emailService.sendMessage(principal.getUserSession(), accountId, draft)));
                    } catch (InvalidArgumentException e) {
                        return ToolResultHelper.error(e.getMessage());
                    } catch (AccountAccessDeniedException e) {
                        return ToolResultHelper.error("Account not found");
                    } catch (AurinkoException e) {
                        ToolErrorLogger.log(log, "Failed to send message", e);
                        return ToolResultHelper.error(insufficientScope(e)
                                ? "This account is not allowed to send mail; reconnect it with connect_account" : FAILURE);
                    } catch (IOException e) {
                        ToolErrorLogger.log(log, "Failed to send message", e);
                        return ToolResultHelper.error(FAILURE);
                    }
                }).build();
    }

    private List<String> recipients(List<?> values) {
        return values.stream().map(value -> {
            if (!(value instanceof String email) || !EmailAddressValidator.isValid(email)) {
                throw new InvalidArgumentException("Invalid recipient email");
            }
            return email;
        }).toList();
    }

    private boolean insufficientScope(AurinkoException failure) {
        if (Integer.valueOf(403).equals(failure.getStatusCode())) {
            return true;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpResponseException response) {
                var error = AurHttpError.fromException(response);
                String code = error == null ? null : error.detailCode();
                if (code != null && code.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").contains("insufficientscope")) {
                    return true;
                }
            }
        }
        return false;
    }
}
