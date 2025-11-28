package com.aratiri.infrastructure.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

@Component
@Order(1)
public class LogFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LogFilter.class);
    private static final List<String> SENSITIVE_FIELDS = Arrays.asList("password", "token", "accessToken", "refreshToken", "jwt");
    public static final int CACHE_LIMIT = 100_000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, CACHE_LIMIT);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long startTime = System.currentTimeMillis();

        filterChain.doFilter(requestWrapper, responseWrapper);

        long timeTaken = System.currentTimeMillis() - startTime;
        logRequest(requestWrapper);
        logResponse(responseWrapper, timeTaken);
        responseWrapper.copyBodyToResponse();
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        logger.info(LogUtils.formatKeyValue("Method", request.getMethod()));
        logger.info(LogUtils.formatKeyValue("URI", request.getRequestURI()));
        logger.debug(LogUtils.formatKeyValue("Query String", request.getQueryString()));
        logger.debug(LogUtils.formatKeyValue("Content Type", request.getContentType()));
        logHeaders(request);
        String requestBody = getRequestBody(request);
        if (!requestBody.isEmpty()) {
            logger.info(LogUtils.formatKeyValue("Request Body", maskSensitiveData(requestBody)));
        }
    }

    private void logResponse(ContentCachingResponseWrapper response, long timeTaken) {
        logger.info(LogUtils.formatKeyValue("Status", response.getStatus()));
        logger.info(LogUtils.formatKeyValue("Time Taken", timeTaken + " ms"));
        String responseBody = getResponseBody(response);
        if (!responseBody.isEmpty()) {
            logger.debug(LogUtils.formatKeyValue("Response Body", maskSensitiveData(responseBody)));
        }
    }

    private void logHeaders(HttpServletRequest request) {
        logger.debug(LogUtils.formatKeyValue("Headers", ""));
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.debug(LogUtils.formatKeyValue("  " + headerName, request.getHeader(headerName)));
        }
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            try {
                return new String(content, request.getCharacterEncoding());
            } catch (UnsupportedEncodingException e) {
                logger.error("Could not read request body", e);
            }
        }
        return "";
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            try {
                return new String(content, response.getCharacterEncoding());
            } catch (UnsupportedEncodingException e) {
                logger.error("Could not read response body", e);
            }
        }
        return "";
    }

    private String maskSensitiveData(String body) {
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            if (jsonNode.isObject()) {
                maskFields((ObjectNode) jsonNode);
            }
            return objectMapper.writeValueAsString(jsonNode);
        } catch (IOException e) {
            return "Not a JSON body, cannot mask.";
        }
    }

    private void maskFields(ObjectNode node) {
        node.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey().toLowerCase();
            if (SENSITIVE_FIELDS.stream().anyMatch(fieldName::contains)) {
                node.put(entry.getKey(), "[REDACTED]");
            } else if (entry.getValue().isObject()) {
                maskFields((ObjectNode) entry.getValue());
            }
        });
    }
}