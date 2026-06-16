package com.intellidesk.workflow;

/**
 * Exception thrown when a workflow node fails or times out during execution.
 * Contains the name of the failed node and the associated request ID for correlation.
 */
public class WorkflowException extends RuntimeException {

    private final String failedNode;
    private final String requestId;

    public WorkflowException(String failedNode, String requestId, Throwable cause) {
        super("Workflow failed at node '" + failedNode + "' for request " + requestId, cause);
        this.failedNode = failedNode;
        this.requestId = requestId;
    }

    public WorkflowException(String failedNode, String requestId, String message) {
        super(message);
        this.failedNode = failedNode;
        this.requestId = requestId;
    }

    public String getFailedNode() {
        return failedNode;
    }

    public String getRequestId() {
        return requestId;
    }
}
