package com.intellidesk.api;

import com.intellidesk.config.AppConfig;
import com.intellidesk.api.dto.SourceReference;
import com.intellidesk.api.dto.VoiceResponse;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.resilience.DegradationHandler;
import com.intellidesk.security.InputSanitizer;
import com.intellidesk.session.SessionManager;
import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.session.model.ConversationHistory;
import com.intellidesk.workflow.WorkflowEngine;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.model.WorkflowState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for the voice chat endpoint.
 * Accepts pre-transcribed text from the browser's Web Speech API
 * and processes it through the same RAG pipeline as text chat.
 *
 * @see <a href="requirements.md">Requirements 3.1-3.8, 3.11-3.13</a>
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final AppConfig appConfig;
    private final InputSanitizer inputSanitizer;
    private final SessionManager sessionManager;
    private final DegradationHandler degradationHandler;
    private final List<WorkflowNode> voiceWorkflowNodes;

    public VoiceController(AppConfig appConfig,
                           InputSanitizer inputSanitizer,
                           SessionManager sessionManager,
                           DegradationHandler degradationHandler,
                           @org.springframework.beans.factory.annotation.Qualifier("voiceWorkflowNodes") List<WorkflowNode> voiceWorkflowNodes) {
        this.appConfig = appConfig;
        this.inputSanitizer = inputSanitizer;
        this.sessionManager = sessionManager;
        this.degradationHandler = degradationHandler;
        this.voiceWorkflowNodes = voiceWorkflowNodes;
    }

    /**
     * Request body for voice chat - accepts pre-transcribed text from browser Web Speech API.
     */
    public record VoiceChatRequest(String session_id, String transcript) {}

    /**
     * Processes a voice chat request with browser-transcribed text.
     * The transcript is processed through the same RAG pipeline as /api/chat.
     *
     * @param request JSON body with session_id and transcript
     * @return VoiceResponse with transcript, answer, sources, and latency
     */
    @PostMapping("/chat")
    public ResponseEntity<?> voiceChat(@RequestBody VoiceChatRequest request) {
        long startTime = System.currentTimeMillis();

        String sessionId = request.session_id();
        String transcript = request.transcript();

        // Check VectorStore availability
        if (degradationHandler.isVectorStoreUnavailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Knowledge base is temporarily unavailable",
                    "status", 503,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Validate session_id as UUID
        if (!isValidUuid(sessionId)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid session_id: must be a valid UUID",
                    "status", 400,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Validate transcript
        if (transcript == null || transcript.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Transcript must not be empty",
                    "status", 400,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Validate message length
        if (transcript.length() > appConfig.maxMessageLength()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Transcript must not exceed " + appConfig.maxMessageLength() + " characters",
                    "status", 400,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Check for malicious input
        if (inputSanitizer.isMalicious(transcript)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Request rejected: input detected as malicious",
                    "status", 400,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Sanitize the transcript
        String sanitizedTranscript = inputSanitizer.sanitize(transcript);

        // Get session context
        ConversationHistory history = sessionManager.getHistory(sessionId);
        List<ConversationExchange> sessionContext = history.exchanges();

        // Build initial workflow state
        String requestId = UUID.randomUUID().toString();
        WorkflowState initialState = new WorkflowState(
                sanitizedTranscript,
                sessionId,
                requestId,
                sessionContext,
                null,
                null,
                null,
                null,
                new HashMap<>()
        );

        // Execute text workflow (same pipeline since transcription happens in browser)
        WorkflowEngine engine = new WorkflowEngine(voiceWorkflowNodes, appConfig);
        WorkflowState finalState = engine.execute(initialState);

        // Measure latency
        long latencyMs = System.currentTimeMillis() - startTime;

        // Build sources from retrieved chunks
        List<SourceReference> sources = buildSources(finalState.retrievedChunks());

        // Build response
        VoiceResponse response = new VoiceResponse(
                sanitizedTranscript,
                finalState.generatedAnswer(),
                sources,
                latencyMs
        );

        // Store exchange in session history
        sessionManager.addExchange(sessionId, new ConversationExchange(
                sanitizedTranscript,
                finalState.generatedAnswer(),
                Instant.now()
        ));

        return ResponseEntity.ok()
                .header("X-Request-ID", requestId)
                .body(response);
    }

    private boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<SourceReference> buildSources(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        return chunks.stream()
                .filter(chunk -> chunk.metadata() != null)
                .map(chunk -> new SourceReference(
                        chunk.metadata().fileName(),
                        chunk.metadata().sectionTitle()
                ))
                .toList();
    }
}
