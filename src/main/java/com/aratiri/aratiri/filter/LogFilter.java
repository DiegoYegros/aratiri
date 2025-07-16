package com.aratiri.aratiri.filter;

import com.aratiri.aratiri.utils.LogUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Enumeration;

@Component
@Order(1)
public class LogFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LogFilter.class);


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        logger.info(LogUtils.formatSectionHeader("START REQUEST LOG"));
        logger.info(LogUtils.formatKeyValue("Method", httpRequest.getMethod()));
        logger.info(LogUtils.formatKeyValue("URI", httpRequest.getRequestURI()));
        logger.info(LogUtils.formatKeyValue("Query String", httpRequest.getQueryString()));
        logger.info(LogUtils.formatKeyValue("Content Type", httpRequest.getContentType()));
        logger.info(LogUtils.formatKeyValue("Headers", ""));
        Enumeration<String> headerNames = httpRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.info(LogUtils.formatKeyValue("  " + headerName, httpRequest.getHeader(headerName)));
        }
        logger.info(LogUtils.formatSectionHeader("END REQUEST LOG", "="));
        chain.doFilter(request, response);
    }
}