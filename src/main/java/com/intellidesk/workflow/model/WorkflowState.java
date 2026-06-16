package com.intellidesk.workflow.model;

import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.session.model.ConversationExchange;

import java.util.List;
import java.util.Map;

/**
 * Immutable state object passed between workflow nodes.
 * Each node receives the accumulated state from previous nodes and returns an updated state.
 */
public record WorkflowState(
        String originalQuery,
        String sessionId,
        String requestId,
        List<ConversationExchange> sessionContext,
        List<RetrievedChunk> retrievedChunks,
        String augmentedPrompt,
        String generatedAnswer,
        String groundingResult,
        Map<String, Object> metadata
) {}
