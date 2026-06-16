package com.intellidesk.config;

/**
 * Configuration properties for circuit breaker behavior.
 */
public class CircuitBreakerProperties {

    private int failureThreshold = 5;
    private int cooldownSeconds = 30;

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
