package com.intellidesk.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultCircuitBreaker state machine implementation.
 */
class DefaultCircuitBreakerTest {

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private DefaultCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new DefaultCircuitBreaker("testService", FAILURE_THRESHOLD, COOLDOWN);
    }

    @Test
    @DisplayName("Initial state should be CLOSED")
    void initialStateShouldBeClosed() {
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
    }

    @Test
    @DisplayName("CLOSED: successful call resets failure counter")
    void closedState_successResetsCounter() {
        // Record some failures (but less than threshold)
        for (int i = 0; i < FAILURE_THRESHOLD - 1; i++) {
            assertThrows(RuntimeException.class, () ->
                    circuitBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }
        assertEquals(FAILURE_THRESHOLD - 1, circuitBreaker.getConsecutiveFailures());

        // Successful call resets counter
        String result = circuitBreaker.execute(() -> "success");
        assertEquals("success", result);
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
    }

    @Test
    @DisplayName("CLOSED: N consecutive failures opens circuit")
    void closedState_consecutiveFailuresOpensCircuit() {
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () ->
                    circuitBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }

        assertEquals(CircuitState.OPEN, circuitBreaker.getState());
        assertEquals(FAILURE_THRESHOLD, circuitBreaker.getConsecutiveFailures());
    }

    @Test
    @DisplayName("CLOSED: interleaved successes prevent opening")
    void closedState_interleavedSuccessesPreventOpening() {
        // 4 failures, then success, then 4 more failures - should NOT open
        for (int i = 0; i < FAILURE_THRESHOLD - 1; i++) {
            assertThrows(RuntimeException.class, () ->
                    circuitBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }
        circuitBreaker.execute(() -> "success"); // resets counter

        for (int i = 0; i < FAILURE_THRESHOLD - 1; i++) {
            assertThrows(RuntimeException.class, () ->
                    circuitBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }

        assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
    }

    @Test
    @DisplayName("OPEN: immediate fail-fast without calling service")
    void openState_immediateFailFast() {
        // Open the circuit
        openCircuit();

        // Verify fail-fast
        AtomicInteger callCount = new AtomicInteger(0);
        CircuitOpenException exception = assertThrows(CircuitOpenException.class, () ->
                circuitBreaker.execute(() -> {
                    callCount.incrementAndGet();
                    return "should not execute";
                }));

        assertEquals(0, callCount.get(), "Service should NOT have been called");
        assertEquals("testService", exception.getServiceName());
    }

    @Test
    @DisplayName("OPEN: after cooldown transitions to HALF_OPEN")
    void openState_afterCooldownTransitionsToHalfOpen() {
        // Use a very short cooldown for testing
        DefaultCircuitBreaker shortCooldownBreaker =
                new DefaultCircuitBreaker("testService", FAILURE_THRESHOLD, Duration.ofMillis(50));

        // Open the circuit
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () ->
                    shortCooldownBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }
        assertEquals(CircuitState.OPEN, shortCooldownBreaker.getState());

        // Wait for cooldown
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // State should now be HALF_OPEN
        assertEquals(CircuitState.HALF_OPEN, shortCooldownBreaker.getState());
    }

    @Test
    @DisplayName("HALF_OPEN: probe succeeds transitions to CLOSED")
    void halfOpenState_probeSucceedsTransitionsToClosed() {
        DefaultCircuitBreaker shortCooldownBreaker =
                new DefaultCircuitBreaker("testService", FAILURE_THRESHOLD, Duration.ofMillis(10));

        // Open the circuit
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () ->
                    shortCooldownBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }

        // Wait for cooldown
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Probe succeeds
        String result = shortCooldownBreaker.execute(() -> "probe success");
        assertEquals("probe success", result);
        assertEquals(CircuitState.CLOSED, shortCooldownBreaker.getState());
        assertEquals(0, shortCooldownBreaker.getConsecutiveFailures());
    }

    @Test
    @DisplayName("HALF_OPEN: probe fails transitions back to OPEN")
    void halfOpenState_probeFailsTransitionsToOpen() {
        DefaultCircuitBreaker shortCooldownBreaker =
                new DefaultCircuitBreaker("testService", FAILURE_THRESHOLD, Duration.ofMillis(10));

        // Open the circuit
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () ->
                    shortCooldownBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }

        // Wait for cooldown
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Probe fails
        assertThrows(RuntimeException.class, () ->
                shortCooldownBreaker.execute(() -> { throw new RuntimeException("probe fail"); }));

        assertEquals(CircuitState.OPEN, shortCooldownBreaker.getState());
    }

    @Test
    @DisplayName("HALF_OPEN: only one probe at a time, others fail-fast")
    void halfOpenState_onlyOneProbeAllowed() {
        DefaultCircuitBreaker shortCooldownBreaker =
                new DefaultCircuitBreaker("testService", FAILURE_THRESHOLD, Duration.ofMillis(10));

        // Open the circuit
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () ->
                    shortCooldownBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }

        // Wait for cooldown
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Ensure in HALF_OPEN state
        assertEquals(CircuitState.HALF_OPEN, shortCooldownBreaker.getState());

        // Use concurrency to test that only one probe goes through
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);
        AtomicInteger probeExecutedCount = new AtomicInteger(0);
        AtomicInteger failFastCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    shortCooldownBreaker.execute(() -> {
                        probeExecutedCount.incrementAndGet();
                        // Simulate some work
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        return "probe";
                    });
                } catch (CircuitOpenException e) {
                    failFastCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        // Exactly one probe should have been executed
        assertEquals(1, probeExecutedCount.get(), "Only one probe should execute");
        assertEquals(4, failFastCount.get(), "Other calls should fail fast");
    }

    @Test
    @DisplayName("Thread-safety: concurrent calls don't corrupt state")
    void threadSafety_concurrentCallsDontCorruptState() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    circuitBreaker.execute(() -> {
                        if (idx % 3 == 0) {
                            throw new RuntimeException("simulated failure");
                        }
                        return "ok";
                    });
                    successCount.incrementAndGet();
                } catch (CircuitOpenException e) {
                    failureCount.incrementAndGet();
                } catch (RuntimeException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // State should be one of the valid states
        CircuitState finalState = circuitBreaker.getState();
        assertTrue(finalState == CircuitState.CLOSED || finalState == CircuitState.OPEN
                || finalState == CircuitState.HALF_OPEN,
                "State should be a valid CircuitState");

        // Total operations should equal 100
        assertEquals(100, successCount.get() + failureCount.get());
    }

    @Test
    @DisplayName("Service name is correctly stored and returned")
    void serviceNameIsCorrect() {
        assertEquals("testService", circuitBreaker.getServiceName());
    }

    /**
     * Helper: drives the circuit breaker to OPEN state.
     */
    private void openCircuit() {
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () ->
                    circuitBreaker.execute(() -> { throw new RuntimeException("fail"); }));
        }
        assertEquals(CircuitState.OPEN, circuitBreaker.getState());
    }
}
