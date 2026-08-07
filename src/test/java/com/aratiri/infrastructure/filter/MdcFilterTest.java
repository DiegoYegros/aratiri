package com.aratiri.infrastructure.filter;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MdcFilterTest {

    private static final String TRACE_ID = "trace-1234567890";
    private static final String SPAN_ID = "span-0987654321";

    private final MdcFilter emptyTracerFilter = new MdcFilter(emptyTracerProvider());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void generatesRequestIdAndSetsResponseHeaderWhenInboundAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter, request, response);

        String requestId = response.getHeader("X-Request-Id");
        assertNotNull(requestId);
        assertEquals(32, requestId.length());
        assertTrue(requestId.matches("[0-9a-f]{32}"), "expected UUID without dashes");
        assertEquals(requestId, mdc.get("requestId"));
        assertEquals("/health", mdc.get("path"));
    }

    @Test
    void propagatesInboundXRequestIdAndEchoesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-Request-Id", "client-correlation-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter, request, response);

        assertEquals("client-correlation-42", mdc.get("requestId"));
        assertEquals("client-correlation-42", response.getHeader("X-Request-Id"));
    }

    @Test
    void fallsBackTraceIdToRequestIdWhenNoTracerOrNoSpan() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter, request, response);

        assertEquals(mdc.get("requestId"), mdc.get("traceId"));
        assertNull(mdc.get("spanId"));
    }

    @Test
    void putsTraceIdAndSpanIdFromActiveSpanWhenTracerPresent() throws Exception {
        MdcFilter filter = new MdcFilter(tracerProvider(activeSpanTracer()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> mdc = captureMdcDuringChain(filter, request, response);

        assertEquals(TRACE_ID, mdc.get("traceId"));
        assertEquals(SPAN_ID, mdc.get("spanId"));
        assertNotEquals(mdc.get("requestId"), mdc.get("traceId"));
    }

    @Test
    void putsUserIdForAuthenticatedPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-42", null, List.of()));
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter,
                new MockHttpServletRequest("GET", "/profile"), new MockHttpServletResponse());

        assertEquals("user-42", mdc.get("userId"));
    }

    @Test
    void putsUserIdFromJwtAuthenticationTokenNameNotPrincipalToString() throws Exception {
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "none").subject("u-1").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(), "bob@example.com"));
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter,
                new MockHttpServletRequest("GET", "/account"), new MockHttpServletResponse());

        assertEquals("bob@example.com", mdc.get("userId"));
        assertFalse(mdc.get("userId").contains("Jwt@"),
                "userId must be the token name (email), not Jwt#toString");
    }

    @Test
    void doesNotPutUserIdForAnonymousAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("anonymousKey", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter,
                new MockHttpServletRequest("GET", "/public"), new MockHttpServletResponse());

        assertNull(mdc.get("userId"));
    }

    @Test
    void doesNotPutUserIdForAuthenticatedAnonymousPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of()));
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter,
                new MockHttpServletRequest("GET", "/health"), new MockHttpServletResponse());

        assertNull(mdc.get("userId"));
    }

    @Test
    void leavesMdcEmptyAfterRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        emptyTracerFilter.doFilter(request, response, (req, res) -> {
        });

        Map<String, String> remaining = MDC.getCopyOfContextMap();
        assertTrue(remaining == null || remaining.isEmpty(), "MDC must not leak between requests");
    }

    @Test
    void inboundRequestIdIsTrimmedAndTruncatedToCapOf64() throws Exception {
        String longId = "x".repeat(80);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-Request-Id", "  " + longId + "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter, request, response);

        String expected = longId.substring(0, 64);
        assertEquals(expected, mdc.get("requestId"));
        assertEquals(expected, response.getHeader("X-Request-Id"));
    }

    @Test
    void ignoresControlCharactersInInboundXRequestIdAndGenerates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-Request-Id", "ab\ncd");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> mdc = captureMdcDuringChain(emptyTracerFilter, request, response);

        String requestId = mdc.get("requestId");
        assertNotNull(requestId);
        assertEquals(32, requestId.length());
        assertTrue(requestId.matches("[0-9a-f]{32}"), "control characters must fall back to a generated UUID");
        assertEquals(requestId, response.getHeader("X-Request-Id"));
    }

    private static Map<String, String> captureMdcDuringChain(MdcFilter filter,
            MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        FilterChain chain = (req, res) -> captured.set(MDC.getCopyOfContextMap());
        filter.doFilter(request, response, chain);
        return captured.get();
    }

    private static ObjectProvider<Tracer> emptyTracerProvider() {
        return new DefaultListableBeanFactory().getBeanProvider(Tracer.class);
    }

    private static ObjectProvider<Tracer> tracerProvider(Tracer tracer) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("tracer", tracer);
        return beanFactory.getBeanProvider(Tracer.class);
    }

    private static Tracer activeSpanTracer() {
        return new StubTracer(new StubSpan(new StubTraceContext(TRACE_ID, SPAN_ID)));
    }

    private static class StubTraceContext implements TraceContext {

        private final String traceId;
        private final String spanId;

        StubTraceContext(String traceId, String spanId) {
            this.traceId = traceId;
            this.spanId = spanId;
        }

        @Override
        public String traceId() {
            return traceId;
        }

        @Override
        public String parentId() {
            return null;
        }

        @Override
        public String spanId() {
            return spanId;
        }

        @Override
        public Boolean sampled() {
            return true;
        }
    }

    private static class StubSpan implements Span {

        private final TraceContext context;

        StubSpan(TraceContext context) {
            this.context = context;
        }

        @Override
        public boolean isNoop() {
            return false;
        }

        @Override
        public TraceContext context() {
            return context;
        }

        @Override
        public Span start() {
            return this;
        }

        @Override
        public Span name(String name) {
            return this;
        }

        @Override
        public Span event(String value) {
            return this;
        }

        @Override
        public Span event(String key, long epochNanos, TimeUnit unit) {
            return this;
        }

        @Override
        public Span tag(String key, String value) {
            return this;
        }

        @Override
        public Span error(Throwable error) {
            return this;
        }

        @Override
        public void end() {
        }

        @Override
        public void end(long epochNanos, TimeUnit unit) {
        }

        @Override
        public void abandon() {
        }

        @Override
        public Span remoteServiceName(String remoteServiceName) {
            return this;
        }

        @Override
        public Span remoteIpAndPort(String remoteIp, int remotePort) {
            return this;
        }
    }

    private static class StubTracer implements Tracer {

        private final Span currentSpan;

        StubTracer(Span currentSpan) {
            this.currentSpan = currentSpan;
        }

        @Override
        public Span nextSpan() {
            return currentSpan;
        }

        @Override
        public Span nextSpan(Span parent) {
            return currentSpan;
        }

        @Override
        public SpanInScope withSpan(Span span) {
            return () -> {
            };
        }

        @Override
        public ScopedSpan startScopedSpan(String name) {
            return null;
        }

        @Override
        public Span.Builder spanBuilder() {
            return null;
        }

        @Override
        public TraceContext.Builder traceContextBuilder() {
            return null;
        }

        @Override
        public CurrentTraceContext currentTraceContext() {
            return null;
        }

        @Override
        public SpanCustomizer currentSpanCustomizer() {
            return null;
        }

        @Override
        public Span currentSpan() {
            return currentSpan;
        }

        @Override
        public Map<String, String> getAllBaggage() {
            return Map.of();
        }

        @Override
        public io.micrometer.tracing.Baggage getBaggage(String name) {
            return null;
        }

        @Override
        public io.micrometer.tracing.Baggage getBaggage(TraceContext context, String name) {
            return null;
        }

        @Override
        public io.micrometer.tracing.Baggage createBaggage(String name) {
            return null;
        }

        @Override
        public io.micrometer.tracing.Baggage createBaggage(String name, String value) {
            return null;
        }
    }
}