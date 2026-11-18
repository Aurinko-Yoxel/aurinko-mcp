package com.yoxel.aurinko.mcp.auth;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class McpSessionOwnerFilter implements Filter {

    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final McpSessionOwners owners;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;
        if (!(httpRequest.getAttribute(BearerAuthFilter.PRINCIPAL_ATTRIBUTE) instanceof McpPrincipal principal)) {
            throw new IllegalStateException("MCP principal is missing from request");
        }
        var owner = McpSessionOwner.of(principal);
        String sessionId = httpRequest.getHeader(SESSION_HEADER);
        if (sessionId != null && !owners.isOwner(sessionId, owner)) {
            httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        boolean delete = sessionId != null && "DELETE".equals(httpRequest.getMethod());
        var removed = new AtomicBoolean();
        Runnable removeIfSuccessful = () -> {
            if (delete && httpResponse.getStatus() < 400 && removed.compareAndSet(false, true)) {
                owners.remove(sessionId);
            }
        };
        var wrappedResponse = new HttpServletResponseWrapper(httpResponse) {
            @Override
            public void setHeader(String name, String value) {
                register(name, value);
                super.setHeader(name, value);
            }

            @Override
            public void addHeader(String name, String value) {
                register(name, value);
                super.addHeader(name, value);
            }

            private void register(String name, String value) {
                if (sessionId == null && SESSION_HEADER.equalsIgnoreCase(name) && value != null) {
                    owners.register(value, owner);
                }
            }
        };
        var wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
            @Override
            public AsyncContext startAsync() {
                return track(super.startAsync(this, wrappedResponse));
            }

            @Override
            public AsyncContext startAsync(ServletRequest asyncRequest, ServletResponse asyncResponse) {
                return track(super.startAsync(asyncRequest, asyncResponse == httpResponse ? wrappedResponse : asyncResponse));
            }

            private AsyncContext track(AsyncContext context) {
                if (delete) {
                    context.addListener(new AsyncListener() {
                        @Override
                        public void onComplete(AsyncEvent event) {
                            removeIfSuccessful.run();
                        }

                        @Override
                        public void onTimeout(AsyncEvent event) {
                        }

                        @Override
                        public void onError(AsyncEvent event) {
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) {
                            event.getAsyncContext().addListener(this);
                        }
                    });
                }
                return context;
            }
        };
        chain.doFilter(wrappedRequest, wrappedResponse);
        if (!wrappedRequest.isAsyncStarted()) {
            removeIfSuccessful.run();
        }
    }
}
