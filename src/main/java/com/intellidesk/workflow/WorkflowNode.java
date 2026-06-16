package com.intellidesk.workflow;

import com.intellidesk.workflow.model.WorkflowState;

/**
 * Represents a single node in the LangGraph-style directed workflow state graph.
 * Each node is a function: State → State.
 */
public interface WorkflowNode {

    /**
     * Returns the unique name of this workflow node.
     */
    String name();

    /**
     * Executes this node's logic, receiving the accumulated state
     * from previous nodes and returning the updated state.
     *
     * @param state the current workflow state
     * @return the updated workflow state
     */
    WorkflowState execute(WorkflowState state);
}
