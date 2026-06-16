package com.intellidesk.resilience;

import com.intellidesk.config.AppConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration that creates one CircuitBreaker instance per external dependency.
 * Uses AppConfig for failure threshold and cooldown seconds.
 */
@Configuration
public class CircuitBreakerConfig {

    @Bean(name = "llmCircuitBreaker")
    public CircuitBreaker llmCircuitBreaker(AppConfig appConfig) {
        return new DefaultCircuitBreaker(
                "llm",
                appConfig.cbFailureThreshold(),
                Duration.ofSeconds(appConfig.cbCooldownSeconds())
        );
    }

    @Bean(name = "vectorStoreCircuitBreaker")
    public CircuitBreaker vectorStoreCircuitBreaker(AppConfig appConfig) {
        return new DefaultCircuitBreaker(
                "vectorStore",
                appConfig.cbFailureThreshold(),
                Duration.ofSeconds(appConfig.cbCooldownSeconds())
        );
    }

    @Bean(name = "sttCircuitBreaker")
    public CircuitBreaker sttCircuitBreaker(AppConfig appConfig) {
        return new DefaultCircuitBreaker(
                "stt",
                appConfig.cbFailureThreshold(),
                Duration.ofSeconds(appConfig.cbCooldownSeconds())
        );
    }
}
