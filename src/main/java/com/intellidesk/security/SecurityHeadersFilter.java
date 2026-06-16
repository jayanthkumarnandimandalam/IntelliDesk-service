package com.intellidesk.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that adds security headers to every HTTP response.
 * <p>
 * Headers added:
 * <ul>
 *   <li>X-Content-Type-Options: nosniff — prevents MIME-type sniffing</li>
 *   <li>X-Frame-Options: DENY — prevents clickjacking via frames</li>
 *   <li>Content-Security-Policy: default-src 'self' — restricts resource loading</li>
 * </ul>
 */
@Component
@Order(1)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("Content-Security-Policy", "default-src 'self'");
        }
        chain.doFilter(request, response);
    }
}
