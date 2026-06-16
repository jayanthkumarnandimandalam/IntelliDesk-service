package com.intellidesk.config;

/**
 * Configuration properties for workflow engine behavior.
 */
public class WorkflowProperties {

    private int nodeTimeoutSeconds = 30;

    public int getNodeTimeoutSeconds() {
        return nodeTimeoutSeconds;
    }

    public void setNodeTimeoutSeconds(int nodeTimeoutSeconds) {
        this.nodeTimeoutSeconds = nodeTimeoutSeconds;
    }
}
