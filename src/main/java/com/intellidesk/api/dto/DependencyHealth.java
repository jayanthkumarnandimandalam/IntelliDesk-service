package com.intellidesk.api.dto;

import java.time.Instant;

/**
 * Health status of a single external dependency.
 */
public record DependencyHealth(
        String status,
        long responseTimeMs,
        Instant lastSuccessfulCheck,
        String circuitBreakerState
) {}
