package com.intellidesk.resilience;

/**
 * Thrown when a circuit breaker is in OPEN state and rejects the call.
 */
public class CircuitOpenException extends RuntimeException {

    private final String serviceName;

    public CircuitOpenException(String serviceName) {
        super("Circuit breaker is open for service: " + serviceName);
        this.serviceName = serviceName;
    }

    public CircuitOpenException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
