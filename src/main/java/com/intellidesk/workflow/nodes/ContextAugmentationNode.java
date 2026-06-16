package com.intellidesk.workflow.nodes;

import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.WorkflowNodeLogger;
import com.intellidesk.workflow.model.WorkflowState;

import java.util.List;

/**
 * Workflow node that constructs the augmented LLM prompt by combining:
 * - System instructions for grounded answering
 * - Retrieved context chunks
 * - Session conversation history
 * - The user's resolved query
 *
 * Produces a prompt designed to elicit a grounded, source-attributable answer.
 */
public class ContextAugmentationNode implements WorkflowNode {

    private static final String NODE_NAME = "ContextAugmentationNode";

    private static final String SYSTEM_INSTRUCTION = """
            You are an IT support assistant. Answer the user's question using ONLY the context provided below.
            If the context does not contain enough information to answer, say "I don't have enough information in my knowledge base to answer this question."
            Always cite which source document(s) you used.
            Do not make up information or extend beyond what is in the provided context.
            """;

    @Override
    public String name() {
        return NODE_NAME;
    }

    @Override
    public WorkflowState execute(WorkflowState state) {
        long startTime = System.currentTimeMillis();

        String prompt = buildPrompt(
                state.originalQuery(),
                state.retrievedChunks(),
                state.sessionContext()
        );

        WorkflowState result = new WorkflowState(
                state.originalQuery(),
                state.sessionId(),
                state.requestId(),
                state.sessionContext(),
                state.retrievedChunks(),
                prompt,
                state.generatedAnswer(),
                state.groundingResult(),
                state.metadata()
        );

        long durationMs = System.currentTimeMillis() - startTime;
        WorkflowNodeLogger.logNodeExecution(NODE_NAME, durationMs, state, result);

        return result;
    }

    private String buildPrompt(String query, List<RetrievedChunk> chunks,
                               List<ConversationExchange> sessionContext) {
        StringBuilder sb = new StringBuilder();

        sb.append(SYSTEM_INSTRUCTION).append("\n\n");

        // Add retrieved context
        sb.append("=== CONTEXT ===\n");
        if (chunks != null && !chunks.isEmpty()) {
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk chunk = chunks.get(i);
                sb.append(String.format("[Source %d: %s - %s]\n",
                        i + 1,
                        chunk.metadata().fileName(),
                        chunk.metadata().sectionTitle() != null ? chunk.metadata().sectionTitle() : ""));
                sb.append(chunk.content()).append("\n\n");
            }
        } else {
            sb.append("No relevant context found.\n\n");
        }

        // Add conversation history if present
        if (sessionContext != null && !sessionContext.isEmpty()) {
            sb.append("=== CONVERSATION HISTORY ===\n");
            for (ConversationExchange exchange : sessionContext) {
                sb.append("User: ").append(exchange.userMessage()).append("\n");
                sb.append("Assistant: ").append(exchange.assistantResponse()).append("\n\n");
            }
        }

        // Add the current query
        sb.append("=== USER QUESTION ===\n");
        sb.append(query).append("\n");

        return sb.toString();
    }
}
