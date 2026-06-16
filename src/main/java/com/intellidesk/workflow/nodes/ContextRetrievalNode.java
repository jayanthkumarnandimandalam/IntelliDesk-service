package com.intellidesk.workflow.nodes;

import com.intellidesk.config.AppConfig;
import com.intellidesk.rag.ChunkRetriever;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.WorkflowNodeLogger;
import com.intellidesk.workflow.model.WorkflowState;

import java.util.List;

/**
 * Workflow node that retrieves relevant context chunks from the vector store.
 * Uses ChunkRetriever to find top-k chunks above the similarity threshold.
 * The retrieval call is protected by a circuit breaker.
 */
public class ContextRetrievalNode implements WorkflowNode {

    private static final String NODE_NAME = "ContextRetrievalNode";

    private final ChunkRetriever chunkRetriever;
    private final CircuitBreaker circuitBreaker;
    private final int topK;
    private final double similarityThreshold;

    public ContextRetrievalNode(ChunkRetriever chunkRetriever, CircuitBreaker circuitBreaker,
                                AppConfig appConfig) {
        this.chunkRetriever = chunkRetriever;
        this.circuitBreaker = circuitBreaker;
        this.topK = appConfig.topK();
        this.similarityThreshold = appConfig.similarityThreshold();
    }

    @Override
    public String name() {
        return NODE_NAME;
    }

    @Override
    public WorkflowState execute(WorkflowState state) {
        long startTime = System.currentTimeMillis();

        String query = state.originalQuery();

        List<RetrievedChunk> chunks = circuitBreaker.execute(
                () -> chunkRetriever.retrieve(query, topK, similarityThreshold)
        );

        WorkflowState result = new WorkflowState(
                state.originalQuery(),
                state.sessionId(),
                state.requestId(),
                state.sessionContext(),
                chunks,
                state.augmentedPrompt(),
                state.generatedAnswer(),
                state.groundingResult(),
                state.metadata()
        );

        long durationMs = System.currentTimeMillis() - startTime;
        WorkflowNodeLogger.logNodeExecution(NODE_NAME, durationMs, state, result);

        return result;
    }
}
