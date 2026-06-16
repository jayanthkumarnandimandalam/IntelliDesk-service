package com.intellidesk.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that generates a UUID v4 request_id for every incoming request,
 * stores it in SLF4J's MDC for automatic inclusion in structured log entries,
 * and returns it in the X-Request-ID response header.
 *
 * Also extracts session_id from the request header (X-Session-ID) and populates
 * it in the MDC for log correlation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String SESSION_ID_HEADER = "X-Session-ID";
    public static final String MDC_REQUEST_ID = "request_id";
    public static final String MDC_SESSION_ID = "session_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        String sessionId = extractSessionId(request);

        MDC.put(MDC_REQUEST_ID, requestId);
        if (sessionId != null && !sessionId.isBlank()) {
            MDC.put(MDC_SESSION_ID, sessionId);
        }

        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_SESSION_ID);
        }
    }

    private String extractSessionId(HttpServletRequest request) {
        // First try to get from request header
        String sessionId = request.getHeader(SESSION_ID_HEADER);
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        // Fallback: session_id may be in query param (for non-POST requests)
        return request.getParameter("session_id");
    }
}
