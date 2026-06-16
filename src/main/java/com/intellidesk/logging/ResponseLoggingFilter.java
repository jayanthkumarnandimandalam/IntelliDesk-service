package com.intellidesk.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that logs HTTP response details including method, path,
 * status code, and latency in milliseconds.
 *
 * Logs at INFO level for successful responses (status < 400) and
 * at ERROR level for client/server errors (status >= 400).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();

            MDC.put("latency_ms", String.valueOf(latencyMs));
            MDC.put("http_method", method);
            MDC.put("http_path", path);
            MDC.put("http_status", String.valueOf(status));

            try {
                if (status >= 400) {
                    logger.error("HTTP {} {} responded {} in {}ms", method, path, status, latencyMs);
                } else {
                    logger.info("HTTP {} {} responded {} in {}ms", method, path, status, latencyMs);
                }
            } finally {
                MDC.remove("latency_ms");
                MDC.remove("http_method");
                MDC.remove("http_path");
                MDC.remove("http_status");
            }
        }
    }
}
