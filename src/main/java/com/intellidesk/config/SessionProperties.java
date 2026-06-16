package com.intellidesk.config;

/**
 * Configuration properties for session management.
 */
public class SessionProperties {

    private int contextWindowSize = 10;
    private int timeoutMinutes = 30;

    public int getContextWindowSize() {
        return contextWindowSize;
    }

    public void setContextWindowSize(int contextWindowSize) {
        this.contextWindowSize = contextWindowSize;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }
}
