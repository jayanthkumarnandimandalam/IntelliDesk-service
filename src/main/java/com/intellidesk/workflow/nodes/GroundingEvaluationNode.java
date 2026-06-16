package com.intellidesk.workflow.nodes;

import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.WorkflowNodeLogger;
import com.intellidesk.workflow.model.WorkflowState;

import java.util.List;

/**
 * Workflow node that evaluates how well the generated answer is grounded
 * in the retrieved context chunks. Classifies grounding as:
 * - "supported": answer content is fully supported by retrieved chunks
 * - "partial": answer contains some content beyond retrieved chunks
 * - "unsupported": no chunks were retrieved or answer has no overlap with context
 */
public class GroundingEvaluationNode implements WorkflowNode {

    private static final String NODE_NAME = "GroundingEvaluationNode";

    static final String SUPPORTED = "supported";
    static final String PARTIAL = "partial";
    static final String UNSUPPORTED = "unsupported";

    @Override
    public String name() {
        return NODE_NAME;
    }

    @Override
    public WorkflowState execute(WorkflowState state) {
        long startTime = System.currentTimeMillis();

        String groundingResult = evaluateGrounding(state.generatedAnswer(), state.retrievedChunks());

        WorkflowState result = new WorkflowState(
                state.originalQuery(),
                state.sessionId(),
                state.requestId(),
                state.sessionContext(),
                state.retrievedChunks(),
                state.augmentedPrompt(),
                state.generatedAnswer(),
                groundingResult,
                state.metadata()
        );

        long durationMs = System.currentTimeMillis() - startTime;
        WorkflowNodeLogger.logNodeExecution(NODE_NAME, durationMs, state, result);

        return result;
    }

    /**
     * Evaluates grounding by checking overlap between the generated answer
     * and the retrieved chunks.
     *
     * Classification logic:
     * - No chunks or empty answer → "unsupported"
     * - High overlap (majority of answer sentences found in chunks) → "supported"
     * - Some overlap → "partial"
     * - No overlap → "unsupported"
     */
    String evaluateGrounding(String answer, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return UNSUPPORTED;
        }

        if (answer == null || answer.isBlank()) {
            return UNSUPPORTED;
        }

        // Build combined context from all chunks
        String combinedContext = chunks.stream()
                .map(RetrievedChunk::content)
                .reduce("", (a, b) -> a + " " + b)
                .toLowerCase();

        // Split answer into sentences for granular evaluation
        String[] sentences = answer.split("[.!?]+");
        if (sentences.length == 0) {
            return UNSUPPORTED;
        }

        int supportedSentences = 0;
        int totalMeaningfulSentences = 0;

        for (String sentence : sentences) {
            String trimmed = sentence.trim().toLowerCase();
            if (trimmed.length() < 5) {
                // Skip very short fragments (e.g., single words or whitespace artifacts)
                continue;
            }
            totalMeaningfulSentences++;

            // Check if key phrases from the sentence appear in the context
            if (hasSignificantOverlap(trimmed, combinedContext)) {
                supportedSentences++;
            }
        }

        if (totalMeaningfulSentences == 0) {
            return UNSUPPORTED;
        }

        double supportRatio = (double) supportedSentences / totalMeaningfulSentences;

        if (supportRatio >= 0.8) {
            return SUPPORTED;
        } else if (supportRatio > 0.0) {
            return PARTIAL;
        } else {
            return UNSUPPORTED;
        }
    }

    /**
     * Checks whether a sentence has significant word overlap with the context.
     * Uses a simple word overlap metric: if more than 50% of meaningful words
     * (length > 3) in the sentence appear in the context, considers it supported.
     */
    private boolean hasSignificantOverlap(String sentence, String context) {
        String[] words = sentence.split("\\s+");
        int meaningfulWords = 0;
        int matchedWords = 0;

        for (String word : words) {
            String cleaned = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (cleaned.length() <= 3) {
                continue; // Skip short/common words
            }
            meaningfulWords++;
            if (context.contains(cleaned)) {
                matchedWords++;
            }
        }

        if (meaningfulWords == 0) {
            return false;
        }

        return (double) matchedWords / meaningfulWords >= 0.5;
    }
}
