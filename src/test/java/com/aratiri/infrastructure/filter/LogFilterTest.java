package com.aratiri.infrastructure.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LogFilterTest {

    private static final String AUTH_SENTINEL = "SENTINEL-AUTH-BEARER-TOKEN-9f3a";
    private static final String COOKIE_SENTINEL = "SENTINEL-COOKIE-SESSION-7c2b";
    private static final String MACAROON_SENTINEL = "SENTINEL-MACAROON-HEX-ab01ef99";
    private static final String API_KEY_SENTINEL = "SENTINEL-X-API-KEY-44dd";
    private static final String QUERY_TOKEN_SENTINEL = "SENTINEL-QUERY-TOKEN-1a2b";
    private static final String REQUEST_BODY_SENTINEL = "SENTINEL-REQUEST-BODY-PASSWORD-88ee";
    private static final String RESPONSE_BODY_SENTINEL = "SENTINEL-RESPONSE-BODY-PREIMAGE-55aa";
    private static final String PROXY_AUTH_SENTINEL = "SENTINEL-PROXY-AUTH-33cc";
    private static final String SET_COOKIE_SENTINEL = "SENTINEL-SET-COOKIE-22bb";

    private final LogFilter logFilter = new LogFilter();
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogFilter.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void doesNotLogSensitiveHeadersQueryOrBodiesAtInfoOrDebug() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
        request.setQueryString("token=" + QUERY_TOKEN_SENTINEL + "&amount=1000");
        request.setParameter("token", QUERY_TOKEN_SENTINEL);
        request.setParameter("amount", "1000");
        request.setContentType("application/json");
        request.addHeader("Authorization", "Bearer " + AUTH_SENTINEL);
        request.addHeader("authorization", "Bearer " + AUTH_SENTINEL);
        request.addHeader("Proxy-Authorization", "Basic " + PROXY_AUTH_SENTINEL);
        request.addHeader("Cookie", "session=" + COOKIE_SENTINEL);
        request.addHeader("Set-Cookie", "id=" + SET_COOKIE_SENTINEL);
        request.addHeader("X-API-Key", API_KEY_SENTINEL);
        request.addHeader("x-api-key", API_KEY_SENTINEL);
        request.addHeader("Macaroon", MACAROON_SENTINEL);
        request.addHeader("macaroon", MACAROON_SENTINEL);
        request.addHeader("X-Auth-Token", AUTH_SENTINEL);
        request.setContent(("{\"password\":\"" + REQUEST_BODY_SENTINEL + "\"}").getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            MockHttpServletResponse httpResponse = (MockHttpServletResponse) res;
            httpResponse.setStatus(202);
            httpResponse.getWriter().write("{\"preimage\":\"" + RESPONSE_BODY_SENTINEL + "\"}");
        };

        logFilter.doFilter(request, response, chain);

        String captured = capturedLogText();
        assertFalse(captured.isBlank(), "expected operational log output");
        assertTrue(captured.contains("POST"), "safe method metadata missing");
        assertTrue(captured.contains("/v1/payments"), "safe URI metadata missing");
        assertTrue(captured.contains("202"), "safe status metadata missing");
        assertTrue(captured.contains("Time Taken"), "safe elapsed-time metadata missing");
        assertTrue(captured.contains("token") || captured.contains("amount"),
                "query parameter names should still be logged");

        assertSentinelAbsent(captured, AUTH_SENTINEL);
        assertSentinelAbsent(captured, COOKIE_SENTINEL);
        assertSentinelAbsent(captured, MACAROON_SENTINEL);
        assertSentinelAbsent(captured, API_KEY_SENTINEL);
        assertSentinelAbsent(captured, QUERY_TOKEN_SENTINEL);
        assertSentinelAbsent(captured, REQUEST_BODY_SENTINEL);
        assertSentinelAbsent(captured, RESPONSE_BODY_SENTINEL);
        assertSentinelAbsent(captured, PROXY_AUTH_SENTINEL);
        assertSentinelAbsent(captured, SET_COOKIE_SENTINEL);

        assertEquals(202, response.getStatus());
        assertTrue(response.getContentAsString().contains(RESPONSE_BODY_SENTINEL),
                "response body must still reach the client");
    }

    @Test
    void preservesSafeMetadataWithoutQueryValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setParameter("token", QUERY_TOKEN_SENTINEL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        logFilter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        String captured = capturedLogText();
        assertTrue(captured.contains("GET"));
        assertTrue(captured.contains("/health"));
        assertTrue(captured.contains("200"));
        assertSentinelAbsent(captured, QUERY_TOKEN_SENTINEL);
        assertFalse(captured.contains("token=" + QUERY_TOKEN_SENTINEL));
    }

    private String capturedLogText() {
        return listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private static void assertSentinelAbsent(String captured, String sentinel) {
        assertFalse(captured.contains(sentinel),
                () -> "captured logs must not contain sentinel: " + sentinel + "\nlogs:\n" + captured);
    }
}
