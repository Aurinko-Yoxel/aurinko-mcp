package com.yoxel.aurinko.mcp.tools;

import com.google.api.client.http.HttpResponseException;
import org.slf4j.Logger;

public class ToolErrorLogger {

    public static void log(Logger logger, String operation, Throwable error) {
        Integer status = null;
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpResponseException responseException) {
                status = responseException.getStatusCode();
                break;
            }
        }
        Throwable cause = error.getCause() == null ? error : error.getCause();
        logger.error("{}: type={}, httpStatus={}, stack={}", operation,
                cause.getClass().getName(), status, cause.getStackTrace());
    }
}
