package com.intellidesk.workflow.nodes;

import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.WorkflowNodeLogger;
import com.intellidesk.workflow.model.WorkflowState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow node that resolves pronouns and references in the user query
 * using session context. For example, "What about that?" becomes
 * "What about [previous topic]?" when conversation history is available.
 *
 * Current implementation: passes query through with basic reference resolution.
 * Can be enhanced with LLM-based coreference resolution.
 */
public class QueryUnderstandingNode implements WorkflowNode {

    private static final String NODE_NAME = "QueryUnderstandingNode";
    private static final String RESOLVED_QUERY_KEY = "resolvedQuery";

    @Override
    public String name() {
        return NODE_NAME;
    }

    @Override
    public WorkflowState execute(WorkflowState state) {
        long startTime = System.currentTimeMillis();

        String originalQuery = state.originalQuery();
        List<ConversationExchange> sessionContext = state.sessionContext();

        String resolvedQuery = resolveReferences(originalQuery, sessionContext);

        // Store the resolved query in metadata for downstream nodes
        Map<String, Object> updatedMetadata = state.metadata() != null
                ? new HashMap<>(state.metadata())
                : new HashMap<>();
        updatedMetadata.put(RESOLVED_QUERY_KEY, resolvedQuery);

        WorkflowState result = new WorkflowState(
                resolvedQuery,
                state.sessionId(),
                state.requestId(),
                state.sessionContext(),
                state.retrievedChunks(),
                state.augmentedPrompt(),
                state.generatedAnswer(),
                state.groundingResult(),
                updatedMetadata
        );

        long durationMs = System.currentTimeMillis() - startTime;
        WorkflowNodeLogger.logNodeExecution(NODE_NAME, durationMs, state, result);

        return result;
    }

    /**
     * Resolves pronouns and references using session context.
     * Simple implementation: if the query contains pronouns like "it", "that", "this"
     * and there is prior context, prepend context hint. Otherwise, pass through.
     */
    private String resolveReferences(String query, List<ConversationExchange> context) {
        if (query == null || query.isBlank()) {
            return query;
        }

        if (context == null || context.isEmpty()) {
            return query;
        }

        // Simple heuristic: if query is very short and contains a pronoun reference,
        // append the last topic from context for better retrieval
        String lowerQuery = query.toLowerCase().trim();
        boolean hasPronouns = lowerQuery.contains(" it") || lowerQuery.contains(" that")
                || lowerQuery.contains(" this") || lowerQuery.startsWith("it ")
                || lowerQuery.startsWith("that ") || lowerQuery.startsWith("this ");

        if (hasPronouns && !context.isEmpty()) {
            ConversationExchange lastExchange = context.get(context.size() - 1);
            String lastUserMessage = lastExchange.userMessage();
            if (lastUserMessage != null && !lastUserMessage.isBlank()) {
                return query + " (context: " + lastUserMessage + ")";
            }
        }

        return query;
    }
}
