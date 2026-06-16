package com.intellidesk.workflow;

import com.intellidesk.workflow.model.WorkflowState;

import java.util.Optional;

/**
 * Functional interface representing a conditional edge in the workflow graph.
 * After a node completes, the engine evaluates registered conditional edges.
 * If the condition is met, the edge returns a short-circuit state that bypasses
 * remaining nodes.
 */
@FunctionalInterface
public interface ConditionalEdge {

    /**
     * Evaluates the condition against the current workflow state.
     *
     * @param state the current state after a node execution
     * @return an Optional containing the short-circuit state if the condition is met,
     *         or empty if the workflow should continue normally
     */
    Optional<WorkflowState> evaluate(WorkflowState state);
}
