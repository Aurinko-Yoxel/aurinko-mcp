package com.yoxel.aurinko.mcp.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthServletTest {

    @Test
    void healthyResponseReturnedWithoutReadingRequest() throws Exception {
        var status = new AtomicInteger();
        var contentType = new AtomicReference<String>();
        Map<String, String> headers = new HashMap<>();
        var body = new StringWriter();
        var writer = new PrintWriter(body);
        var request = proxy(HttpServletRequest.class, (instance, method, args) -> {
            throw new AssertionError("Health must not read the request");
        });
        var response = proxy(HttpServletResponse.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "setStatus" -> status.set((int) args[0]);
                case "setContentType" -> contentType.set((String) args[0]);
                case "setHeader" -> headers.put((String) args[0], (String) args[1]);
                case "getWriter" -> {
                    return writer;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
            return null;
        });

        new HealthServlet().doGet(request, response);

        assertThat(status).hasValue(200);
        assertThat(contentType).hasValue("application/json");
        assertThat(body.toString()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(headers).containsEntry("Cache-Control", "no-store");
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
