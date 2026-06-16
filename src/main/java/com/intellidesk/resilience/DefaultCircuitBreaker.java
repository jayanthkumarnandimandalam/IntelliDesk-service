package com.intellidesk.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Default implementation of the CircuitBreaker interface using a state machine.
 * Thread-safe via AtomicReference for state and AtomicBoolean for probe control.
 *
 * States:
 * - CLOSED: normal operation, counts consecutive failures
 * - OPEN: fail-fast, waits for cool-down period
 * - HALF_OPEN: allows exactly one probe call
 */
public class DefaultCircuitBreaker implements CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCircuitBreaker.class);

    private final String serviceName;
    private final int failureThreshold;
    private final Duration cooldownDuration;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>(Instant.MIN);
    private final AtomicBoolean probeInProgress = new AtomicBoolean(false);

    public DefaultCircuitBreaker(String serviceName, int failureThreshold, Duration cooldownDuration) {
        this.serviceName = serviceName;
        this.failureThreshold = failureThreshold;
        this.cooldownDuration = cooldownDuration;
    }

    @Override
    public CircuitState getState() {
        // Check if we should transition from OPEN to HALF_OPEN
        if (state.get() == CircuitState.OPEN && isCooldownElapsed()) {
            transitionTo(CircuitState.HALF_OPEN);
        }
        return state.get();
    }

    @Override
    public <T> T execute(Supplier<T> action) throws CircuitOpenException {
        CircuitState currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return executeClosed(action);
            case OPEN:
                return executeOpen(action);
            case HALF_OPEN:
                return executeHalfOpen(action);
            default:
                throw new IllegalStateException("Unknown circuit state: " + currentState);
        }
    }

    @Override
    public void recordSuccess() {
        consecutiveFailures.set(0);
    }

    @Override
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(Instant.now());
        if (failures >= failureThreshold && state.get() == CircuitState.CLOSED) {
            transitionTo(CircuitState.OPEN);
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    private <T> T executeClosed(Supplier<T> action) {
        try {
            T result = action.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }

    private <T> T executeOpen(Supplier<T> action) throws CircuitOpenException {
        if (isCooldownElapsed()) {
            transitionTo(CircuitState.HALF_OPEN);
            return executeHalfOpen(action);
        }
        throw new CircuitOpenException(serviceName,
                "Circuit breaker is open for service: " + serviceName + ". Failing fast.");
    }

    private <T> T executeHalfOpen(Supplier<T> action) throws CircuitOpenException {
        if (probeInProgress.compareAndSet(false, true)) {
            try {
                T result = action.get();
                // Probe succeeded: transition to CLOSED
                consecutiveFailures.set(0);
                probeInProgress.set(false);
                transitionTo(CircuitState.CLOSED);
                return result;
            } catch (Exception e) {
                // Probe failed: transition back to OPEN
                lastFailureTime.set(Instant.now());
                probeInProgress.set(false);
                transitionTo(CircuitState.OPEN);
                throw e;
            }
        } else {
            // Another probe is in progress, fail-fast
            throw new CircuitOpenException(serviceName,
                    "Circuit breaker is half-open for service: " + serviceName + ". Probe in progress, failing fast.");
        }
    }

    private boolean isCooldownElapsed() {
        Instant lastFailure = lastFailureTime.get();
        return Instant.now().isAfter(lastFailure.plus(cooldownDuration));
    }

    private void transitionTo(CircuitState newState) {
        CircuitState previousState = state.getAndSet(newState);
        if (previousState != newState) {
            logStateTransition(previousState, newState);
        }
    }

    private void logStateTransition(CircuitState fromState, CircuitState toState) {
        logger.info("{\"event\":\"circuit_breaker_transition\","
                        + "\"service\":\"{}\","
                        + "\"fromState\":\"{}\","
                        + "\"toState\":\"{}\","
                        + "\"timestamp\":\"{}\","
                        + "\"failureCount\":{}}",
                serviceName, fromState, toState, Instant.now(), consecutiveFailures.get());
    }
}
