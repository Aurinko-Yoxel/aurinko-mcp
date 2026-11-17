package com.yoxel.aurinko.mcp.tools;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.yoxel.aurinko.mcp.aurinko.AurinkoException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolErrorLoggerTest {

    @Test
    void nestedHttpStatusLoggedWithoutRequestOrResponseContent() {
        var upstream = new HttpResponseException.Builder(503, "private-status-text", new HttpHeaders())
                .setContent("private-response-body").setMessage("https://private.example.com/path private-subject private-location")
                .build();
        var failure = new AurinkoException(new IOException("private-session private-description", upstream));
        var logger = (Logger) LoggerFactory.getLogger(ToolErrorLoggerTest.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            ToolErrorLogger.log(logger, "Failed to load events from Aurinko", failure);

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("httpStatus=503")
                    .doesNotContain("private-session", "private-subject", "private-description", "private-location",
                            "private-status-text", "private-response-body", "https://private.example.com/path");
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
