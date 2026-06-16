package com.intellidesk.security;

import com.intellidesk.config.AppConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration that restricts cross-origin requests to configured allowed origins.
 * <p>
 * Reads allowed origins from {@link AppConfig#corsAllowedOrigins()} (comma-separated),
 * and uses Spring's built-in {@link CorsFilter} which correctly handles preflight
 * OPTIONS requests and adds appropriate CORS response headers.
 * <p>
 * Allowed methods: GET, POST, PUT, DELETE, OPTIONS.
 * Allowed headers: all.
 */
@Configuration
public class CorsConfig {

    private final AppConfig appConfig;

    public CorsConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Bean
    public CorsFilter corsFilter() {
        String originsStr = appConfig.corsAllowedOrigins();
        List<String> allowedOrigins = Arrays.stream(originsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
