package com.intellidesk.resilience;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Property-based tests for DefaultCircuitBreaker state machine.
 * Validates Properties 37, 38, 39, and 40 from the design document.
 */
class CircuitBreakerPropertyTest {

    // ========================================================================
    // Property 37: Circuit breaker failure counter and state transition
    // For any sequence of calls in CLOSED state, the circuit SHALL open if and
    // only if N consecutive failures occur (where N is the configured threshold),
    // and any successful call SHALL reset the consecutive failure counter to zero.
    // Validates: Requirements 16.2, 16.9
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 37: Circuit breaker failure counter and state transition")
    void circuitOpensAfterExactlyNConsecutiveFailures(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold
    ) {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(
                "test-service", failureThreshold, Duration.ofMinutes(5));

        // Record exactly (threshold - 1) failures: should remain CLOSED
        for (int i = 0; i < failureThreshold - 1; i++) {
            cb.recordFailure();
            assert cb.getState() == CircuitState.CLOSED :
                    "Circuit should remain CLOSED after " + (i + 1) + " failures (threshold=" + failureThreshold + ")";
        }

        // The Nth failure should open the circuit
        cb.recordFailure();
        assert cb.getState() == CircuitState.OPEN :
                "Circuit should be OPEN after " + failureThreshold + " consecutive failures";
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 37: Circuit breaker failure counter and state transition")
    void successResetsFailureCounterPreventsOpening(
            @ForAll @IntRange(min = 2, max = 10) int failureThreshold,
            @ForAll @IntRange(min = 1, max = 9) int failuresBeforeReset
    ) {
        // Ensure we don't exceed threshold before the reset
        int effectiveFailures = Math.min(failuresBeforeReset, failureThreshold - 1);

        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(
                "test-service", failureThreshold, Duration.ofMinutes(5));

        // Record some failures (but less than threshold)
        for (int i = 0; i < effectiveFailures; i++) {
            cb.recordFailure();
        }

        // A success should reset the counter
        cb.recordSuccess();

        assert cb.getState() == CircuitState.CLOSED :
                "Circuit should remain CLOSED after success resets counter";
        assert cb.getConsecutiveFailures() == 0 :
                "Failure counter should be 0 after success but was " + cb.getConsecutiveFailures();

        // Now, even after (threshold - 1) more failures it should stay CLOSED
        for (int i = 0; i < failureThreshold - 1; i++) {
            cb.recordFailure();
        }
        assert cb.getState() == CircuitState.CLOSED :
                "Circuit should remain CLOSED after (threshold-1) failures following a reset";
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 37: Circuit breaker failure counter and state transition")
    void executeSuccessResetsCounterViaAction(
            @ForAll @IntRange(min = 2, max = 10) int failureThreshold
    ) {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(
                "test-service", failureThreshold, Duration.ofMinutes(5));

        // Record (threshold - 1) failures
        for (int i = 0; i < failureThreshold - 1; i++) {
            cb.recordFailure();
        }

        // Execute a successful action — should reset the counter
        String result = cb.execute(() -> "success");
        assert "success".equals(result);
        assert cb.getConsecutiveFailures() == 0 :
                "Failure counter should be reset after successful execute";
        assert cb.getState() == CircuitState.CLOSED :
                "State should remain CLOSED after successful execute";
    }

    // ========================================================================
    // Property 38: Circuit breaker open state fail-fast
    // For any request in OPEN state, the system SHALL immediately fail-fast
    // without attempting the external service call.
    // Validates: Requirements 16.3
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 38: Circuit breaker open state fail-fast")
    void openStateFailsFastWithoutCallingService(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold,
            @ForAll @IntRange(min = 1, max = 20) int attemptsWhileOpen
    ) {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(
                "test-service", failureThreshold, Duration.ofMinutes(5));

        // Force the circuit to OPEN by reaching the failure threshold
        for (int i = 0; i < failureThreshold; i++) {
            cb.recordFailure();
        }
        assert cb.getState() == CircuitState.OPEN :
                "Circuit should be OPEN after reaching threshold";

        // Attempt multiple calls while OPEN - none should reach the supplier
        AtomicInteger serviceCallCount = new AtomicInteger(0);
        Supplier<String> trackedAction = () -> {
            serviceCallCount.incrementAndGet();
            return "should-not-be-called";
        };

        for (int i = 0; i < attemptsWhileOpen; i++) {
            try {
                cb.execute(trackedAction);
                assert false : "execute() should have thrown CircuitOpenException";
            } catch (CircuitOpenException e) {
                // Expected behavior — fail-fast
                assert e.getServiceName().equals("test-service") :
                        "Exception should reference the correct service name";
            }
        }

        assert serviceCallCount.get() == 0 :
                "Service should not have been called while circuit is OPEN, but was called "
                        + serviceCallCount.get() + " times";
    }

    // ========================================================================
    // Property 39: Circuit breaker half-open probe control
    // In HALF_OPEN state, exactly one request SHALL be permitted as a probe,
    // all other requests SHALL fail-fast until the probe completes.
    // Validates: Requirements 16.4
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 39: Circuit breaker half-open probe control")
    void halfOpenAllowsExactlyOneProbe(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold,
            @ForAll @IntRange(min = 2, max = 10) int concurrentAttempts
    ) throws InterruptedException {
        // Use a 50ms cooldown: long enough to observe OPEN state, short enough to
        // transition to HALF_OPEN after sleep
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(
                "test-service", failureThreshold, Duration.ofMillis(50));

        // Force OPEN
        for (int i = 0; i < failureThreshold; i++) {
            cb.recordFailure();
        }
        // Immediately after recordFailure (which sets lastFailureTime=now),
        // 50ms cooldown has NOT elapsed yet
        assert cb.getState() == CircuitState.OPEN :
                "Circuit should be OPEN after reaching threshold";

        // Wait for cooldown to elapse so it transitions to HALF_OPEN on next getState()
        Thread.sleep(100);
        assert cb.getState() == CircuitState.HALF_OPEN :
                "Circuit should be HALF_OPEN after cooldown";

        // Use a latch to block the probe so we can test concurrent access
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch probeRelease = new CountDownLatch(1);
        AtomicInteger probeCount = new AtomicInteger(0);
        AtomicInteger failFastCount = new AtomicInteger(0);

        // Start probe thread — this will acquire the probeInProgress flag
        Thread probeThread = new Thread(() -> {
            try {
                cb.execute(() -> {
                    probeCount.incrementAndGet();
                    probeStarted.countDown();
                    try {
                        probeRelease.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "probe-result";
                });
            } catch (CircuitOpenException e) {
                failFastCount.incrementAndGet();
            }
        });
        probeThread.start();

        // Wait for probe to start executing
        probeStarted.await();

        // Now try concurrent requests while probe is in progress — all should fail-fast
        // The state is HALF_OPEN with probeInProgress=true, so executeHalfOpen
        // will fail-fast on all concurrent requests
        for (int i = 0; i < concurrentAttempts; i++) {
            try {
                cb.execute(() -> "concurrent-request");
                assert false : "Concurrent request should fail-fast while probe is in progress";
            } catch (CircuitOpenException e) {
                failFastCount.incrementAndGet();
            }
        }

        // Release the probe
        probeRelease.countDown();
        probeThread.join(5000);

        assert probeCount.get() == 1 :
                "Exactly one probe should have been allowed, but " + probeCount.get() + " were executed";
        assert failFastCount.get() == concurrentAttempts :
                "All " + concurrentAttempts + " concurrent requests should have failed fast, but only "
                        + failFastCount.get() + " did";
    }

    // ========================================================================
    // Property 40: Circuit breaker probe resolution
    // Probe success → CLOSED, probe failure → OPEN.
    // If the probe receives a successful response, the circuit transitions to
    // CLOSED. If the probe fails, the circuit returns to OPEN.
    // Validates: Requirements 16.5, 16.6
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 40: Circuit breaker probe resolution")
    void probeSuccessTransitionsToClosed(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold
    ) throws InterruptedException {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(
                "test-service", failureThreshold, Duration.ofMillis(50));

        // Force OPEN
        for (int i = 0; i < failureThreshold; i++) {
            cb.recordFailure();
        }

        // Wait for cooldown to elapse so it transitions to HALF_OPEN
        Thread.sleep(100);
        assert cb.getState() == CircuitState.HALF_OPEN :
                "Circuit should be HALF_OPEN after cooldown";

        // Execute a successful probe
        String result = cb.execute(() -> "probe-success");
        assert "probe-success".equals(result) :
                "Probe should return the supplier's result";
        assert cb.getState() == CircuitState.CLOSED :
                "Circuit should transition to CLOSED after successful probe";
        assert cb.getConsecutiveFailures() == 0 :
                "Failure counter should be reset after successful probe";
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 40: Circuit breaker probe resolution")
    void probeFailureTransitionsBackToOpen(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold
    ) throws InterruptedException {
        // Use a cooldown of 50ms: short enough to transition quickly,
        // long enough that after probe failure (which resets lastFailureTime),
        // the new cooldown hasn't elapsed yet when we check getState()
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(
                "test-service", failureThreshold, Duration.ofMillis(50));

        // Force OPEN
        for (int i = 0; i < failureThreshold; i++) {
            cb.recordFailure();
        }

        // Wait for cooldown to elapse so it transitions to HALF_OPEN
        Thread.sleep(100);
        assert cb.getState() == CircuitState.HALF_OPEN :
                "Circuit should be HALF_OPEN after cooldown";

        // Execute a failing probe
        try {
            cb.execute(() -> {
                throw new RuntimeException("probe-failure");
            });
            assert false : "Probe should have propagated the exception";
        } catch (RuntimeException e) {
            assert "probe-failure".equals(e.getMessage()) :
                    "Should propagate the original exception";
        }

        // After probe failure, lastFailureTime is set to now and state is OPEN.
        // Since cooldown is 50ms and we check immediately, it should still be OPEN.
        assert cb.getState() == CircuitState.OPEN :
                "Circuit should transition back to OPEN after failed probe";
    }
}
