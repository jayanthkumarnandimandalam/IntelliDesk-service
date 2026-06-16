package com.intellidesk.workflow.nodes;

import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.WorkflowNodeLogger;
import com.intellidesk.workflow.model.WorkflowState;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow node that transcribes audio input using Speech-to-Text.
 * Only invoked on voice request paths where audio bytes are present in state metadata.
 * Calls STT service via circuit breaker for resilience.
 */
public class TranscriptionNode implements WorkflowNode {

    private static final String NODE_NAME = "TranscriptionNode";
    private static final String AUDIO_BYTES_KEY = "audioBytes";
    private static final String TRANSCRIPT_KEY = "transcript";

    private final SttService sttService;
    private final CircuitBreaker circuitBreaker;

    public TranscriptionNode(SttService sttService, CircuitBreaker circuitBreaker) {
        this.sttService = sttService;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public String name() {
        return NODE_NAME;
    }

    @Override
    public WorkflowState execute(WorkflowState state) {
        long startTime = System.currentTimeMillis();

        Map<String, Object> metadata = state.metadata();
        if (metadata == null || !metadata.containsKey(AUDIO_BYTES_KEY)) {
            // No audio data — pass through (text path)
            return state;
        }

        byte[] audioBytes = (byte[]) metadata.get(AUDIO_BYTES_KEY);

        String transcript = circuitBreaker.execute(() -> sttService.transcribe(audioBytes));

        // Put transcript in metadata and use it as the original query
        Map<String, Object> updatedMetadata = new HashMap<>(metadata);
        updatedMetadata.put(TRANSCRIPT_KEY, transcript);

        long durationMs = System.currentTimeMillis() - startTime;
        WorkflowNodeLogger.logNodeExecution(NODE_NAME, durationMs, state,
                new WorkflowState(
                        transcript,
                        state.sessionId(),
                        state.requestId(),
                        state.sessionContext(),
                        state.retrievedChunks(),
                        state.augmentedPrompt(),
                        state.generatedAnswer(),
                        state.groundingResult(),
                        updatedMetadata
                ));

        return new WorkflowState(
                transcript,
                state.sessionId(),
                state.requestId(),
                state.sessionContext(),
                state.retrievedChunks(),
                state.augmentedPrompt(),
                state.generatedAnswer(),
                state.groundingResult(),
                updatedMetadata
        );
    }
}
