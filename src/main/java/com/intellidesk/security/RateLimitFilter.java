package com.intellidesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Servlet filter that enforces rate limiting on API requests.
 * Extracts session_id from the X-Session-ID header or request body,
 * and returns HTTP 429 with Retry-After header when the limit is exceeded.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String SESSION_ID_HEADER = "X-Session-ID";
    private static final String API_PATH_PREFIX = "/api/";

    private final SlidingWindowRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(SlidingWindowRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String requestPath = request.getRequestURI();

        // Only rate-limit API endpoints
        if (!requestPath.startsWith(API_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // Skip health endpoint from rate limiting
        if (requestPath.equals("/api/health")) {
            chain.doFilter(request, response);
            return;
        }

        // Extract session_id from header
        String sessionId = request.getHeader(SESSION_ID_HEADER);

        if (sessionId == null || sessionId.isBlank()) {
            // If no session ID provided, use remote address as fallback
            sessionId = request.getRemoteAddr();
        }

        if (!rateLimiter.isAllowed(sessionId)) {
            long retryAfter = rateLimiter.getRetryAfterSeconds(sessionId);
            log.warn("Rate limit exceeded for session: {}", sessionId);

            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");

            Map<String, Object> errorBody = Map.of(
                    "error", "Rate limit exceeded. Please try again later.",
                    "status", 429,
                    "request_id", UUID.randomUUID().toString(),
                    "timestamp", Instant.now().toString()
            );

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }

        chain.doFilter(request, response);
    }
}
