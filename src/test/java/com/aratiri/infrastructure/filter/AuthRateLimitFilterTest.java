package com.aratiri.infrastructure.filter;

import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties.AuthRateLimit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    @Mock
    private FilterChain filterChain;

    private Clock clock;
    private AuthRateLimiter rateLimiter;
    private AuthRateLimitFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        AuthRateLimit config = new AuthRateLimit();
        config.setEnabled(true);
        config.setRequestsPerWindow(2);
        config.setWindow(Duration.ofSeconds(30));
        config.setMaximumKeys(100);
        rateLimiter = new AuthRateLimiter(config, clock);

        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        properties.setAuthRateLimit(config);
        filter = new AuthRateLimitFilter(rateLimiter, properties);
    }

    @Test
    void allowsNonSensitiveRoutesWithoutConsumingBudget() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/auth/me");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shortCircuitsSensitiveRouteWhenExceeded() throws Exception {
        MockHttpServletRequest first = sensitiveLogin("10.0.0.1");
        MockHttpServletRequest second = sensitiveLogin("10.0.0.1");
        MockHttpServletRequest third = sensitiveLogin("10.0.0.1");

        filter.doFilter(first, new MockHttpServletResponse(), filterChain);
        filter.doFilter(second, new MockHttpServletResponse(), filterChain);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(third, denied, filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), denied.getStatus());
        assertEquals("30", denied.getHeader(HttpHeaders.RETRY_AFTER));
        assertEquals("application/json", denied.getContentType());

        JsonNode body = objectMapper.readTree(denied.getContentAsString());
        assertEquals("Too many requests. Please try again later.", body.get("message").asText());
        assertEquals(429, body.get("status").asInt());
        assertTrue(body.hasNonNull("timestamp"));
    }

    @Test
    void xForwardedForDoesNotCreateSeparateBucket() throws Exception {
        MockHttpServletRequest first = sensitiveLogin("10.0.0.1");
        first.addHeader("X-Forwarded-For", "203.0.113.9");
        MockHttpServletRequest second = sensitiveLogin("10.0.0.1");
        second.addHeader("X-Forwarded-For", "198.51.100.7");
        MockHttpServletRequest third = sensitiveLogin("10.0.0.1");
        third.addHeader("X-Forwarded-For", "192.0.2.1");

        filter.doFilter(first, new MockHttpServletResponse(), filterChain);
        filter.doFilter(second, new MockHttpServletResponse(), filterChain);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(third, denied, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), denied.getStatus());
        verify(filterChain, times(2)).doFilter(any(), any());
    }

    @Test
    void separateRemoteAddressesRemainIndependent() throws Exception {
        filter.doFilter(sensitiveLogin("10.0.0.1"), new MockHttpServletResponse(), filterChain);
        filter.doFilter(sensitiveLogin("10.0.0.1"), new MockHttpServletResponse(), filterChain);
        filter.doFilter(sensitiveLogin("10.0.0.2"), new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(3)).doFilter(any(), any());
    }

    @Test
    void disabledFilterPassesThrough() throws Exception {
        AuthRateLimit config = new AuthRateLimit();
        config.setEnabled(false);
        config.setRequestsPerWindow(1);
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        properties.setAuthRateLimit(config);
        AuthRateLimitFilter disabled = new AuthRateLimitFilter(rateLimiter, properties);

        for (int i = 0; i < 5; i++) {
            disabled.doFilter(sensitiveLogin("10.0.0.1"), new MockHttpServletResponse(), filterChain);
        }
        verify(filterChain, times(5)).doFilter(any(), any());
    }

    @Test
    void shortCircuitsPublicPaymentRequestGetWhenExceeded() throws Exception {
        MockHttpServletRequest first = publicPaymentRequest("10.0.0.1", "/r/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        MockHttpServletRequest second = publicPaymentRequest("10.0.0.1", "/r/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        MockHttpServletRequest third = publicPaymentRequest("10.0.0.1", "/r/cccccccccccccccccccccccccccccccc");

        filter.doFilter(first, new MockHttpServletResponse(), filterChain);
        filter.doFilter(second, new MockHttpServletResponse(), filterChain);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(third, denied, filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), denied.getStatus());
        assertEquals("30", denied.getHeader(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void bucketsDistinctPublicIdsTogetherForSameRemote() throws Exception {
        filter.doFilter(publicPaymentRequest("10.0.0.1", "/r/id1"), new MockHttpServletResponse(), filterChain);
        filter.doFilter(publicPaymentRequest("10.0.0.1", "/r/id2"), new MockHttpServletResponse(), filterChain);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(publicPaymentRequest("10.0.0.1", "/r/id3"), denied, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), denied.getStatus());
        verify(filterChain, times(2)).doFilter(any(), any());
    }

    private static MockHttpServletRequest sensitiveLogin(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static MockHttpServletRequest publicPaymentRequest(String remoteAddr, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
