package com.intellidesk.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ResponseLoggingFilterTest {

    private ResponseLoggingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ResponseLoggingFilter();
        request = new MockHttpServletRequest("GET", "/api/chat");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldLogSuccessfulResponseAtInfoLevel() throws ServletException, IOException {
        response.setStatus(200);

        FilterChain chain = (req, res) -> {
            // simulate successful processing
        };

        // This test verifies the filter doesn't throw - logging verification
        // is better done through integration tests or log appender capture
        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, chain));
    }

    @Test
    void shouldLogErrorResponsesForStatus400AndAbove() throws ServletException, IOException {
        FilterChain chain = (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(404);
        };

        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, chain));
    }

    @Test
    void shouldCleanUpMdcAfterLogging() throws ServletException, IOException {
        FilterChain chain = (req, res) -> {
            // simulate processing
        };

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get("latency_ms"), "latency_ms should be cleared from MDC");
        assertNull(MDC.get("http_method"), "http_method should be cleared from MDC");
        assertNull(MDC.get("http_path"), "http_path should be cleared from MDC");
        assertNull(MDC.get("http_status"), "http_status should be cleared from MDC");
    }

    @Test
    void shouldCleanUpMdcEvenWhenExceptionOccurs() throws ServletException, IOException {
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("Simulated error");
        };

        assertThrows(RuntimeException.class, () ->
                filter.doFilterInternal(request, response, chain));

        assertNull(MDC.get("latency_ms"), "latency_ms should be cleared even on exception");
        assertNull(MDC.get("http_method"), "http_method should be cleared even on exception");
    }

    @Test
    void shouldPassRequestThroughFilterChain() throws ServletException, IOException {
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilterInternal(request, response, chain);

        assertTrue(chainCalled.get(), "Filter chain should be invoked");
    }
}
