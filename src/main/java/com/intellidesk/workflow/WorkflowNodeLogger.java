package com.intellidesk.workflow;

import com.intellidesk.workflow.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for structured JSON logging of workflow node executions.
 * Logs node name, duration, and input/output state key summaries.
 */
public final class WorkflowNodeLogger {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowNodeLogger.class);

    private WorkflowNodeLogger() {
        // Utility class, no instantiation
    }

    /**
     * Logs the execution of a workflow node as structured JSON.
     *
     * @param nodeName   the name of the executed node
     * @param durationMs the execution duration in milliseconds
     * @param input      the input state before node execution
     * @param output     the output state after node execution
     */
    public static void logNodeExecution(String nodeName, long durationMs,
                                        WorkflowState input, WorkflowState output) {
        List<String> inputKeys = getNonNullStateKeys(input);
        List<String> outputKeys = getNonNullStateKeys(output);

        logger.info(
                "{\"event\":\"workflow_node_executed\","
                        + "\"node\":\"{}\","
                        + "\"duration_ms\":{},"
                        + "\"input_state_keys\":{},\"input_state_key_count\":{},"
                        + "\"output_state_keys\":{},\"output_state_key_count\":{}}",
                nodeName, durationMs,
                inputKeys, inputKeys.size(),
                outputKeys, outputKeys.size()
        );
    }

    /**
     * Logs a workflow node failure as structured JSON.
     *
     * @param nodeName  the name of the failed node
     * @param requestId the request ID for correlation
     * @param error     the exception that caused the failure
     */
    public static void logNodeFailure(String nodeName, String requestId, Throwable error) {
        logger.error(
                "{\"event\":\"workflow_node_failed\","
                        + "\"node\":\"{}\","
                        + "\"request_id\":\"{}\","
                        + "\"error_type\":\"{}\","
                        + "\"error_message\":\"{}\"}",
                nodeName, requestId,
                error.getClass().getSimpleName(),
                error.getMessage()
        );
    }

    /**
     * Logs a workflow node timeout as structured JSON.
     *
     * @param nodeName       the name of the timed-out node
     * @param requestId      the request ID for correlation
     * @param timeoutSeconds the configured timeout that was exceeded
     */
    public static void logNodeTimeout(String nodeName, String requestId, int timeoutSeconds) {
        logger.error(
                "{\"event\":\"workflow_node_timeout\","
                        + "\"node\":\"{}\","
                        + "\"request_id\":\"{}\","
                        + "\"timeout_seconds\":{}}",
                nodeName, requestId, timeoutSeconds
        );
    }

    private static List<String> getNonNullStateKeys(WorkflowState state) {
        List<String> keys = new ArrayList<>();
        if (state == null) {
            return keys;
        }
        if (state.originalQuery() != null) keys.add("originalQuery");
        if (state.sessionId() != null) keys.add("sessionId");
        if (state.requestId() != null) keys.add("requestId");
        if (state.sessionContext() != null) keys.add("sessionContext");
        if (state.retrievedChunks() != null) keys.add("retrievedChunks");
        if (state.augmentedPrompt() != null) keys.add("augmentedPrompt");
        if (state.generatedAnswer() != null) keys.add("generatedAnswer");
        if (state.groundingResult() != null) keys.add("groundingResult");
        if (state.metadata() != null) keys.add("metadata");
        return keys;
    }
}
