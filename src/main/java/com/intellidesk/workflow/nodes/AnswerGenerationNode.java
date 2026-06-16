package com.intellidesk.workflow.nodes;

import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.WorkflowNodeLogger;
import com.intellidesk.workflow.model.WorkflowState;

/**
 * Workflow node that generates an answer by calling the LLM service
 * with the augmented prompt. The LLM call is protected by a circuit breaker.
 */
public class AnswerGenerationNode implements WorkflowNode {

    private static final String NODE_NAME = "AnswerGenerationNode";

    private final LlmService llmService;
    private final CircuitBreaker circuitBreaker;

    public AnswerGenerationNode(LlmService llmService, CircuitBreaker circuitBreaker) {
        this.llmService = llmService;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public String name() {
        return NODE_NAME;
    }

    @Override
    public WorkflowState execute(WorkflowState state) {
        long startTime = System.currentTimeMillis();

        String augmentedPrompt = state.augmentedPrompt();
        if (augmentedPrompt == null || augmentedPrompt.isBlank()) {
            throw new IllegalStateException("No augmented prompt available for answer generation");
        }

        String generatedAnswer = circuitBreaker.execute(() -> llmService.generate(augmentedPrompt));

        WorkflowState result = new WorkflowState(
                state.originalQuery(),
                state.sessionId(),
                state.requestId(),
                state.sessionContext(),
                state.retrievedChunks(),
                state.augmentedPrompt(),
                generatedAnswer,
                state.groundingResult(),
                state.metadata()
        );

        long durationMs = System.currentTimeMillis() - startTime;
        WorkflowNodeLogger.logNodeExecution(NODE_NAME, durationMs, state, result);

        return result;
    }
}
