package com.aratiri.infrastructure.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagates the X-Request-Id header and populates the SLF4J MDC with
 * request/span correlation keys (requestId, traceId, spanId, userId, path)
 * for the duration of each request.
 */
@Component
@Order(-90)
public class MdcFilter extends OncePerRequestFilter {

    private static final int X_REQUEST_ID_MAX_LENGTH = 64;

    private final ObjectProvider<Tracer> tracerProvider;

    public MdcFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String requestId = resolveRequestId(request);
            response.setHeader("X-Request-Id", requestId);
            MDC.put("requestId", requestId);
            putSpanIds(requestId);
            putUserId();
            MDC.put("path", request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader("X-Request-Id");
        if (inbound != null) {
            String trimmed = inbound.trim();
            if (!trimmed.isEmpty() && trimmed.chars().allMatch(c -> c >= 0x21 && c <= 0x7E)) {
                return trimmed.length() <= X_REQUEST_ID_MAX_LENGTH
                        ? trimmed
                        : trimmed.substring(0, X_REQUEST_ID_MAX_LENGTH);
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void putSpanIds(String requestId) {
        Tracer tracer = tracerProvider.getIfAvailable();
        Span currentSpan = tracer != null ? tracer.currentSpan() : null;
        if (currentSpan != null) {
            MDC.put("traceId", currentSpan.context().traceId());
            MDC.put("spanId", currentSpan.context().spanId());
        } else {
            MDC.put("traceId", requestId);
        }
    }

    private void putUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        Object principal = authentication.getPrincipal();
        if (principal == null || principal.toString().equals("anonymous")) {
            return;
        }
        MDC.put("userId", principal.toString());
    }
}