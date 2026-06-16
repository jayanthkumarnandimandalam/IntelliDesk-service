package com.intellidesk.workflow;

import com.intellidesk.config.AppConfig;
import com.intellidesk.workflow.model.WorkflowState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LangGraph-style directed workflow state graph engine.
 * Executes a sequence of {@link WorkflowNode} instances in defined order,
 * passing accumulated state between nodes. Supports conditional edges that
 * can short-circuit the workflow, per-node timeouts, and structured failure logging.
 */
public class WorkflowEngine {

    private final List<WorkflowNode> nodes;
    private final Map<String, List<ConditionalEdge>> conditionalEdges;
    private final int nodeTimeoutSeconds;

    /**
     * Creates a WorkflowEngine with the given nodes and configuration.
     *
     * @param nodes     the ordered list of workflow nodes to execute
     * @param appConfig application configuration providing nodeTimeoutSeconds
     */
    public WorkflowEngine(List<WorkflowNode> nodes, AppConfig appConfig) {
        this.nodes = List.copyOf(nodes);
        this.conditionalEdges = new HashMap<>();
        this.nodeTimeoutSeconds = appConfig.nodeTimeoutSeconds();
    }

    /**
     * Registers a conditional edge that is evaluated after the specified node completes.
     * If the edge's condition is met, the workflow short-circuits and returns the edge's state.
     *
     * @param afterNodeName the name of the node after which the condition is evaluated
     * @param edge          the conditional edge to evaluate
     */
    public void addConditionalEdge(String afterNodeName, ConditionalEdge edge) {
        conditionalEdges.computeIfAbsent(afterNodeName, k -> new ArrayList<>()).add(edge);
    }

    /**
     * Executes the workflow by running each node sequentially, passing accumulated state.
     * <p>
     * If a node throws an exception or exceeds its timeout, execution halts immediately
     * and a {@link WorkflowException} is thrown with the failed node name and request ID.
     * <p>
     * After each node, registered conditional edges are evaluated. If any edge's condition
     * is met, the workflow short-circuits and returns the edge's result state.
     *
     * @param initialState the initial workflow state
     * @return the final accumulated state after all nodes (or short-circuit state)
     * @throws WorkflowException if any node fails or times out
     */
    public WorkflowState execute(WorkflowState initialState) {
        WorkflowState currentState = initialState;
        String requestId = initialState.requestId();

        for (WorkflowNode node : nodes) {
            long startTime = System.currentTimeMillis();

            try {
                currentState = executeWithTimeout(node, currentState);
            } catch (TimeoutException e) {
                WorkflowNodeLogger.logNodeTimeout(node.name(), requestId, nodeTimeoutSeconds);
                throw new WorkflowException(
                        node.name(), requestId,
                        new TimeoutException("Node '" + node.name() + "' exceeded timeout of "
                                + nodeTimeoutSeconds + " seconds")
                );
            } catch (WorkflowException e) {
                throw e;
            } catch (Exception e) {
                WorkflowNodeLogger.logNodeFailure(node.name(), requestId, e);
                throw new WorkflowException(node.name(), requestId, e);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            WorkflowNodeLogger.logNodeExecution(node.name(), durationMs, initialState, currentState);

            // Evaluate conditional edges after this node
            Optional<WorkflowState> shortCircuit = evaluateConditionalEdges(node.name(), currentState);
            if (shortCircuit.isPresent()) {
                return shortCircuit.get();
            }
        }

        return currentState;
    }

    private WorkflowState executeWithTimeout(WorkflowNode node, WorkflowState state)
            throws TimeoutException {
        CompletableFuture<WorkflowState> future = CompletableFuture.supplyAsync(
                () -> node.execute(state)
        );

        try {
            return future.get(nodeTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Workflow interrupted at node: " + node.name(), e);
        }
    }

    private Optional<WorkflowState> evaluateConditionalEdges(String nodeName, WorkflowState state) {
        List<ConditionalEdge> edges = conditionalEdges.get(nodeName);
        if (edges == null || edges.isEmpty()) {
            return Optional.empty();
        }

        for (ConditionalEdge edge : edges) {
            Optional<WorkflowState> result = edge.evaluate(state);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the list of nodes in execution order (immutable view).
     */
    public List<WorkflowNode> getNodes() {
        return nodes;
    }

    /**
     * Executes the workflow up to and including the context retrieval step,
     * stopping before answer generation and grounding evaluation.
     * Used in LLM degradation mode (raw retrieval).
     *
     * Nodes whose name contains "AnswerGeneration" or "GroundingEvaluation" are skipped.
     *
     * @param initialState the initial workflow state
     * @return the state after retrieval nodes have completed
     * @throws WorkflowException if any executed node fails or times out
     */
    public WorkflowState executeUntilRetrieval(WorkflowState initialState) {
        WorkflowState currentState = initialState;
        String requestId = initialState.requestId();

        for (WorkflowNode node : nodes) {
            // Skip LLM-dependent nodes
            String nodeName = node.name();
            if (nodeName.contains("AnswerGeneration") || nodeName.contains("GroundingEvaluation")) {
                continue;
            }

            long startTime = System.currentTimeMillis();

            try {
                currentState = executeWithTimeout(node, currentState);
            } catch (TimeoutException e) {
                WorkflowNodeLogger.logNodeTimeout(node.name(), requestId, nodeTimeoutSeconds);
                throw new WorkflowException(
                        node.name(), requestId,
                        new TimeoutException("Node '" + node.name() + "' exceeded timeout of "
                                + nodeTimeoutSeconds + " seconds")
                );
            } catch (WorkflowException e) {
                throw e;
            } catch (Exception e) {
                WorkflowNodeLogger.logNodeFailure(node.name(), requestId, e);
                throw new WorkflowException(node.name(), requestId, e);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            WorkflowNodeLogger.logNodeExecution(node.name(), durationMs, initialState, currentState);

            // Evaluate conditional edges after this node
            Optional<WorkflowState> shortCircuit = evaluateConditionalEdges(node.name(), currentState);
            if (shortCircuit.isPresent()) {
                return shortCircuit.get();
            }
        }

        return currentState;
    }

    /**
     * Returns the configured per-node timeout in seconds.
     */
    public int getNodeTimeoutSeconds() {
        return nodeTimeoutSeconds;
    }
}
