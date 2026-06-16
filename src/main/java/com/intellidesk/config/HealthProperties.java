package com.intellidesk.config;

/**
 * Configuration properties for health check behavior.
 */
public class HealthProperties {

    private int checkTimeoutSeconds = 5;

    public int getCheckTimeoutSeconds() {
        return checkTimeoutSeconds;
    }

    public void setCheckTimeoutSeconds(int checkTimeoutSeconds) {
        this.checkTimeoutSeconds = checkTimeoutSeconds;
    }
}
