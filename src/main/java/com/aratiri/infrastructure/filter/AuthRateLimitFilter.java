package com.aratiri.infrastructure.filter;

import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties;
import com.aratiri.infrastructure.web.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Applies in-process rate limits to sensitive public auth endpoints and public
 * payment-request lookups ({@code GET /r/**}).
 * <p>
 * Ordered after {@link LogFilter} ({@code @Order(1)}) and
 * {@link PublicPaymentRequestCacheControlFilter} ({@code @Order(2)}) within the application
 * servlet filter chain so request/response status logging still observes 429s and public
 * payment-request responses retain {@code Cache-Control: no-store} even when short-circuited.
 * Spring Security's filter chain defaults to order {@code -100}, so this filter is
 * <em>not</em> guaranteed to run before Security; for {@code permitAll} auth routes Security
 * continues and this filter can still short-circuit the remaining chain. A denied request
 * never reaches MVC controllers or auth application services (covered by the integration spy
 * on {@code AuthPort}).
 * <p>
 * Client identity is {@link HttpServletRequest#getRemoteAddr()} only —
 * {@code X-Forwarded-For} is intentionally ignored because this service does not sanitize
 * forwarded headers; deploy a trusted proxy that rewrites the remote address when client
 * IPs matter.
 */
@Component
@Order(3)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final Set<String> SENSITIVE_AUTH_PATHS = Set.of(
            "/v1/auth/login",
            "/v1/auth/register",
            "/v1/auth/verify",
            "/v1/auth/forgot-password",
            "/v1/auth/reset-password",
            "/v1/auth/refresh",
            "/v1/auth/exchange",
            "/v1/auth/sso/google"
    );

    private static final String RATE_LIMIT_MESSAGE = "Too many requests. Please try again later.";
    private static final String PUBLIC_PAYMENT_REQUEST_PREFIX = "/r/";
    private static final String PUBLIC_PAYMENT_REQUEST_BUCKET = "/r";

    private final AuthRateLimiter rateLimiter;
    private final boolean enabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthRateLimitFilter(AuthRateLimiter rateLimiter, AratiriSecurityProperties securityProperties) {
        this.rateLimiter = rateLimiter;
        this.enabled = securityProperties.getAuthRateLimit().isEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !isRateLimitedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = buildKey(request);
        AuthRateLimiter.Decision decision = rateLimiter.tryAcquire(key);
        if (decision.permitted()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (log.isInfoEnabled()) {
            log.info("Auth rate limit exceeded for {} {}", request.getMethod(), normalizedPath(request));
        }
        writeTooManyRequests(response, decision.retryAfterSeconds());
    }

    private boolean isRateLimitedRequest(HttpServletRequest request) {
        return isSensitiveAuthRequest(request) || isPublicPaymentRequestGet(request);
    }

    private boolean isSensitiveAuthRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return SENSITIVE_AUTH_PATHS.contains(normalizedPath(request));
    }

    private boolean isPublicPaymentRequestGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = normalizedPath(request);
        return path.startsWith(PUBLIC_PAYMENT_REQUEST_PREFIX) && path.length() > PUBLIC_PAYMENT_REQUEST_PREFIX.length();
    }

    private String buildKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            remote = "unknown";
        }
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (isPublicPaymentRequestGet(request)) {
            // Bucket all public payment-request lookups together so opaque IDs cannot explode keyspace.
            return remote + "|" + method + "|" + PUBLIC_PAYMENT_REQUEST_BUCKET;
        }
        return remote + "|" + method + "|" + normalizedPath(request);
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        // Preserve no-store if an earlier filter already set it; reaffirm for /r short-circuits.
        if (!response.containsHeader(HttpHeaders.CACHE_CONTROL)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        ErrorResponse body = new ErrorResponse(
                RATE_LIMIT_MESSAGE,
                HttpStatus.TOO_MANY_REQUESTS.value(),
                Instant.now().toString()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
