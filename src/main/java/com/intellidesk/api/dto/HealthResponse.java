package com.intellidesk.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for the health check endpoint.
 */
public record HealthResponse(
        Map<String, DependencyHealth> dependencies,
        String overallStatus,
        Instant timestamp
) {}
