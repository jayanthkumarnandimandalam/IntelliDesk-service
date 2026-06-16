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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationFilterTest {

    private RequestCorrelationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestCorrelationFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateRequestIdAndSetResponseHeader() throws ServletException, IOException {
        FilterChain chain = (req, res) -> {
            // No-op
        };

        filter.doFilterInternal(request, response, chain);

        String headerValue = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(headerValue, "X-Request-ID header should be set");
        assertDoesNotThrow(() -> UUID.fromString(headerValue),
                "X-Request-ID should be a valid UUID");
    }

    @Test
    void shouldPopulateMdcWithRequestIdDuringFilterChain() throws ServletException, IOException {
        AtomicReference<String> capturedRequestId = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedRequestId.set(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
        };

        filter.doFilterInternal(request, response, chain);

        assertNotNull(capturedRequestId.get(), "MDC request_id should be populated during filter chain");
        assertDoesNotThrow(() -> UUID.fromString(capturedRequestId.get()),
                "MDC request_id should be a valid UUID");
    }

    @Test
    void shouldClearMdcAfterFilterChainCompletes() throws ServletException, IOException {
        FilterChain chain = (req, res) -> {
            // verify MDC is set during execution
            assertNotNull(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
        };

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID),
                "MDC request_id should be cleared after filter completes");
        assertNull(MDC.get(RequestCorrelationFilter.MDC_SESSION_ID),
                "MDC session_id should be cleared after filter completes");
    }

    @Test
    void shouldExtractSessionIdFromHeader() throws ServletException, IOException {
        String sessionId = UUID.randomUUID().toString();
        request.addHeader(RequestCorrelationFilter.SESSION_ID_HEADER, sessionId);

        AtomicReference<String> capturedSessionId = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedSessionId.set(MDC.get(RequestCorrelationFilter.MDC_SESSION_ID));
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(sessionId, capturedSessionId.get(),
                "MDC session_id should match the header value");
    }

    @Test
    void shouldExtractSessionIdFromQueryParam() throws ServletException, IOException {
        String sessionId = UUID.randomUUID().toString();
        request.setParameter("session_id", sessionId);

        AtomicReference<String> capturedSessionId = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedSessionId.set(MDC.get(RequestCorrelationFilter.MDC_SESSION_ID));
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(sessionId, capturedSessionId.get(),
                "MDC session_id should match the query param value");
    }

    @Test
    void shouldPreferHeaderOverQueryParamForSessionId() throws ServletException, IOException {
        String headerSessionId = "header-session-id";
        String paramSessionId = "param-session-id";
        request.addHeader(RequestCorrelationFilter.SESSION_ID_HEADER, headerSessionId);
        request.setParameter("session_id", paramSessionId);

        AtomicReference<String> capturedSessionId = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedSessionId.set(MDC.get(RequestCorrelationFilter.MDC_SESSION_ID));
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(headerSessionId, capturedSessionId.get(),
                "Header session_id should take precedence over query param");
    }

    @Test
    void shouldHandleMissingSessionIdGracefully() throws ServletException, IOException {
        AtomicReference<String> capturedSessionId = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedSessionId.set(MDC.get(RequestCorrelationFilter.MDC_SESSION_ID));
        };

        filter.doFilterInternal(request, response, chain);

        assertNull(capturedSessionId.get(),
                "MDC session_id should be null when not provided");
    }

    @Test
    void shouldGenerateUniqueRequestIdsPerRequest() throws ServletException, IOException {
        AtomicReference<String> firstId = new AtomicReference<>();
        AtomicReference<String> secondId = new AtomicReference<>();

        FilterChain chain1 = (req, res) -> {
            firstId.set(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
        };
        FilterChain chain2 = (req, res) -> {
            secondId.set(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
        };

        filter.doFilterInternal(request, response, chain1);
        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), chain2);

        assertNotEquals(firstId.get(), secondId.get(),
                "Each request should get a unique request_id");
    }

    @Test
    void shouldClearMdcEvenWhenExceptionOccurs() throws ServletException, IOException {
        FilterChain chain = (req, res) -> {
            assertNotNull(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
            throw new RuntimeException("Simulated error");
        };

        assertThrows(RuntimeException.class, () ->
                filter.doFilterInternal(request, response, chain));

        assertNull(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID),
                "MDC should be cleared even on exception");
    }

    @Test
    void shouldSetRequestIdInResponseHeaderMatchingMdc() throws ServletException, IOException {
        AtomicReference<String> mdcRequestId = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcRequestId.set(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
        };

        filter.doFilterInternal(request, response, chain);

        String headerValue = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertEquals(mdcRequestId.get(), headerValue,
                "X-Request-ID header should match the MDC request_id");
    }
}
