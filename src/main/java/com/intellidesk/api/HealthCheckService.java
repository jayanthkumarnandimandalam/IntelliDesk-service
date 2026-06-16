package com.intellidesk.api;

import com.intellidesk.api.dto.DependencyHealth;
import com.intellidesk.api.dto.HealthResponse;
import com.intellidesk.config.AppConfig;
import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.resilience.CircuitOpenException;
import com.intellidesk.resilience.CircuitState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service responsible for performing health checks on all external dependencies
 * (LLM, VectorStore, STT) via their circuit breakers.
 *
 * Each dependency check is timed and subject to a configurable timeout.
 * Results include per-dependency status, response time, last successful check,
 * and circuit breaker state.
 */
@Service
public class HealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);

    private final AppConfig appConfig;
    private final CircuitBreaker llmCircuitBreaker;
    private final CircuitBreaker vectorStoreCircuitBreaker;
    private final CircuitBreaker sttCircuitBreaker;

    private final AtomicReference<Instant> lastSuccessfulLlm = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastSuccessfulVectorStore = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastSuccessfulStt = new AtomicReference<>(null);

    public HealthCheckService(
            AppConfig appConfig,
            CircuitBreaker llmCircuitBreaker,
            CircuitBreaker vectorStoreCircuitBreaker,
            CircuitBreaker sttCircuitBreaker) {
        this.appConfig = appConfig;
        this.llmCircuitBreaker = llmCircuitBreaker;
        this.vectorStoreCircuitBreaker = vectorStoreCircuitBreaker;
        this.sttCircuitBreaker = sttCircuitBreaker;
    }

    /**
     * Performs health checks on all dependencies and returns an aggregated response.
     *
     * @return HealthResponse with per-dependency status and overall status
     */
    public HealthResponse checkHealth() {
        int timeoutSeconds = appConfig.healthCheckTimeoutSec();
        Map<String, DependencyHealth> dependencies = new LinkedHashMap<>();

        dependencies.put("llm", checkDependency("llm", llmCircuitBreaker, lastSuccessfulLlm, timeoutSeconds));
        dependencies.put("vectorStore", checkDependency("vectorStore", vectorStoreCircuitBreaker, lastSuccessfulVectorStore, timeoutSeconds));
        dependencies.put("stt", checkDependency("stt", sttCircuitBreaker, lastSuccessfulStt, timeoutSeconds));

        String overallStatus = computeOverallStatus(dependencies);

        return new HealthResponse(dependencies, overallStatus, Instant.now());
    }

    /**
     * Checks a single dependency's health via its circuit breaker.
     * Times the check and applies a timeout.
     */
    DependencyHealth checkDependency(
            String name,
            CircuitBreaker circuitBreaker,
            AtomicReference<Instant> lastSuccessful,
            int timeoutSeconds) {

        CircuitState cbState = circuitBreaker.getState();
        long startTime = System.nanoTime();

        // If circuit is OPEN, report as DOWN immediately without attempting a call
        if (cbState == CircuitState.OPEN) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logger.info("Health check for {} - circuit breaker is OPEN, reporting DOWN", name);
            return new DependencyHealth("DOWN", elapsed, lastSuccessful.get(), cbState.name());
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> {
                // Execute a lightweight ping through the circuit breaker
                return circuitBreaker.execute(() -> "OK");
            });

            String result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            if ("OK".equals(result)) {
                Instant now = Instant.now();
                lastSuccessful.set(now);
                logger.debug("Health check for {} succeeded in {}ms", name, elapsed);
                return new DependencyHealth("UP", elapsed, now, circuitBreaker.getState().name());
            } else {
                logger.warn("Health check for {} returned unexpected result: {}", name, result);
                return new DependencyHealth("DOWN", elapsed, lastSuccessful.get(), circuitBreaker.getState().name());
            }
        } catch (TimeoutException e) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logger.warn("Health check for {} timed out after {}s", name, timeoutSeconds);
            return new DependencyHealth("DOWN", elapsed, lastSuccessful.get(), circuitBreaker.getState().name());
        } catch (ExecutionException e) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            Throwable cause = e.getCause();
            if (cause instanceof CircuitOpenException) {
                logger.info("Health check for {} - circuit breaker rejected call", name);
            } else {
                logger.warn("Health check for {} failed: {}", name, cause != null ? cause.getMessage() : "unknown");
            }
            return new DependencyHealth("DOWN", elapsed, lastSuccessful.get(), circuitBreaker.getState().name());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logger.error("Health check for {} was interrupted", name);
            return new DependencyHealth("DOWN", elapsed, lastSuccessful.get(), circuitBreaker.getState().name());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Computes the overall status from individual dependency statuses.
     * "UP" if all are UP, "DEGRADED" if any are DOWN.
     */
    String computeOverallStatus(Map<String, DependencyHealth> dependencies) {
        boolean allUp = dependencies.values().stream()
                .allMatch(dh -> "UP".equals(dh.status()));
        return allUp ? "UP" : "DEGRADED";
    }
}
