package com.intellidesk.api;

import com.intellidesk.api.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the system health check endpoint.
 * Returns readiness status for all external dependencies.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    private final HealthCheckService healthCheckService;

    public HealthController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    /**
     * GET /api/health
     *
     * Checks readiness of LLM, VectorStore, and STT services.
     * Returns 200 if all UP, 503 if any DOWN (DEGRADED), 500 on internal error.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        try {
            HealthResponse response = healthCheckService.checkHealth();

            if ("UP".equals(response.overallStatus())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(503).body(response);
            }
        } catch (Exception e) {
            logger.error("Health check encountered an internal error", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
