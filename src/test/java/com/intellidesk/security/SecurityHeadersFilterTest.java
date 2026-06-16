package com.intellidesk.security;

import com.intellidesk.config.AppConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SecurityHeadersFilter and CorsConfig CORS enforcement.
 */
class SecurityHeadersFilterTest {

    @Nested
    @DisplayName("SecurityHeadersFilter")
    class SecurityHeaders {

        private SecurityHeadersFilter filter;
        private MockHttpServletRequest request;
        private MockHttpServletResponse response;
        private FilterChain chain;

        @BeforeEach
        void setUp() {
            filter = new SecurityHeadersFilter();
            request = new MockHttpServletRequest();
            response = new MockHttpServletResponse();
            chain = mock(FilterChain.class);
        }

        @Test
        @DisplayName("Should set X-Content-Type-Options header to nosniff")
        void shouldSetXContentTypeOptions() throws ServletException, IOException {
            filter.doFilter(request, response, chain);

            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should set X-Frame-Options header to DENY")
        void shouldSetXFrameOptions() throws ServletException, IOException {
            filter.doFilter(request, response, chain);

            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should set Content-Security-Policy header to default-src 'self'")
        void shouldSetContentSecurityPolicy() throws ServletException, IOException {
            filter.doFilter(request, response, chain);

            assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should set all three security headers in a single request")
        void shouldSetAllHeaders() throws ServletException, IOException {
            filter.doFilter(request, response, chain);

            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
            assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'");
        }

        @Test
        @DisplayName("Should continue the filter chain after setting headers")
        void shouldContinueFilterChain() throws ServletException, IOException {
            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("CorsFilter via CorsConfig")
    class CorsFilterTests {

        private CorsFilter corsFilter;
        private MockHttpServletRequest request;
        private MockHttpServletResponse response;
        private FilterChain chain;

        @BeforeEach
        void setUp() {
            AppConfig appConfig = mock(AppConfig.class);
            when(appConfig.corsAllowedOrigins()).thenReturn("http://localhost:5173,https://app.intellidesk.com");
            CorsConfig corsConfig = new CorsConfig(appConfig);
            corsFilter = corsConfig.corsFilter();
            request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setRequestURI("/api/chat");
            response = new MockHttpServletResponse();
            chain = mock(FilterChain.class);
        }

        @Test
        @DisplayName("Should allow request with configured origin")
        void shouldAllowConfiguredOrigin() throws ServletException, IOException {
            request.addHeader("Origin", "http://localhost:5173");

            corsFilter.doFilter(request, response, chain);

            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should allow request with second configured origin")
        void shouldAllowSecondConfiguredOrigin() throws ServletException, IOException {
            request.addHeader("Origin", "https://app.intellidesk.com");

            corsFilter.doFilter(request, response, chain);

            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://app.intellidesk.com");
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should reject request from non-configured origin")
        void shouldRejectDisallowedOrigin() throws ServletException, IOException {
            request.addHeader("Origin", "https://evil.example.com");

            corsFilter.doFilter(request, response, chain);

            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        }

        @Test
        @DisplayName("Should allow request without Origin header (same-origin)")
        void shouldAllowRequestWithoutOriginHeader() throws ServletException, IOException {
            corsFilter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle OPTIONS preflight request correctly")
        void shouldHandlePreflightRequest() throws ServletException, IOException {
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:5173");
            request.addHeader("Access-Control-Request-Method", "POST");

            corsFilter.doFilter(request, response, chain);

            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
            assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("POST");
        }

        @Test
        @DisplayName("Should reject origin that partially matches but is not exact")
        void shouldRejectPartialOriginMatch() throws ServletException, IOException {
            request.addHeader("Origin", "http://localhost:5173.evil.com");

            corsFilter.doFilter(request, response, chain);

            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        }
    }
}
