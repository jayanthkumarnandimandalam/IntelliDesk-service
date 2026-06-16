package com.intellidesk.resilience;

import java.util.function.Supplier;

/**
 * Circuit breaker interface for protecting external service calls.
 * One instance per external dependency (LLM, Vector Store, STT).
 */
public interface CircuitBreaker {

    /**
     * Returns the current state of the circuit breaker.
     */
    CircuitState getState();

    /**
     * Executes the given action through the circuit breaker.
     * In CLOSED state: executes the action and tracks failures.
     * In OPEN state: fails fast without executing the action.
     * In HALF_OPEN state: allows exactly one probe call.
     *
     * @param action the action to execute
     * @param <T> the return type of the action
     * @return the result of the action
     * @throws CircuitOpenException if the circuit is open and the call is rejected
     */
    <T> T execute(Supplier<T> action) throws CircuitOpenException;

    /**
     * Records a successful external service call.
     */
    void recordSuccess();

    /**
     * Records a failed external service call.
     */
    void recordFailure();
}
