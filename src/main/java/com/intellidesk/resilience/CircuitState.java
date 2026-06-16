package com.intellidesk.resilience;

/**
 * Represents the possible states of a circuit breaker.
 */
public enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
