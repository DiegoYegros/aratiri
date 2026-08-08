package com.aratiri.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

@Component
@Order(1)
public class LogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }
        long startTime = System.currentTimeMillis();
        filterChain.doFilter(request, response);
        long timeTaken = System.currentTimeMillis() - startTime;
        logRequest(request);
        logResponse(response, timeTaken);
    }

    private void logRequest(HttpServletRequest request) {
        if (log.isInfoEnabled()) {
            log.info(LogUtils.formatKeyValue("Method", request.getMethod()));
            log.info(LogUtils.formatKeyValue("URI", request.getRequestURI()));
        }
        if (log.isDebugEnabled()) {
            log.debug(LogUtils.formatKeyValue("Content Type", request.getContentType()));
            logHeaderNames(request);
            logQueryParameterNames(request);
        }
    }

    private void logResponse(HttpServletResponse response, long timeTaken) {
        if (log.isInfoEnabled()) {
            log.info(LogUtils.formatKeyValue("Status", response.getStatus()));
            log.info(LogUtils.formatKeyValue("Time Taken", timeTaken + " ms"));
        }
    }

    private void logHeaderNames(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null || !headerNames.hasMoreElements()) {
            return;
        }
        if (log.isDebugEnabled()) {
            String names = String.join(", ", Collections.list(headerNames));
            log.debug(LogUtils.formatKeyValue("Header Names", names));
        }
    }

    private void logQueryParameterNames(HttpServletRequest request) {
        Enumeration<String> parameterNames = request.getParameterNames();
        if (parameterNames == null || !parameterNames.hasMoreElements()) {
            return;
        }
        if (log.isDebugEnabled()) {
            String names = String.join(", ", Collections.list(parameterNames));
            log.debug(LogUtils.formatKeyValue("Query Parameter Names", names));
        }
    }
}
