package com.intellidesk.api;

import com.intellidesk.config.AppConfig;
import com.intellidesk.api.dto.ChatRequest;
import com.intellidesk.api.dto.ChatResponse;
import com.intellidesk.api.dto.SourceReference;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.resilience.DegradationHandler;
import com.intellidesk.security.InputSanitizer;
import com.intellidesk.session.SessionManager;
import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.session.model.ConversationHistory;
import com.intellidesk.workflow.WorkflowEngine;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.model.WorkflowState;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for the text chat endpoint.
 * Validates input, runs the text workflow, and returns a ChatResponse.
 *
 * @see <a href="requirements.md">Requirements 1.1, 1.4-1.11</a>
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final AppConfig appConfig;
    private final InputSanitizer inputSanitizer;
    private final SessionManager sessionManager;
    private final DegradationHandler degradationHandler;
    private final List<WorkflowNode> textWorkflowNodes;

    public ChatController(AppConfig appConfig,
                          InputSanitizer inputSanitizer,
                          SessionManager sessionManager,
                          DegradationHandler degradationHandler,
                          @org.springframework.beans.factory.annotation.Qualifier("textWorkflowNodes") List<WorkflowNode> textWorkflowNodes) {
        this.appConfig = appConfig;
        this.inputSanitizer = inputSanitizer;
        this.sessionManager = sessionManager;
        this.degradationHandler = degradationHandler;
        this.textWorkflowNodes = textWorkflowNodes;
    }

    /**
     * Processes a text chat request through the RAG workflow pipeline.
     *
     * @param request the validated chat request containing sessionId and message
     * @return ChatResponse with answer, sources, grounding note, and latency
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();

        // Check VectorStore availability - if down, cannot process any chat requests
        if (degradationHandler.isVectorStoreUnavailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Knowledge base is temporarily unavailable",
                    "status", 503,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Validate session_id as UUID
        if (!isValidUuid(request.sessionId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid session_id: must be a valid UUID",
                    "status", 400,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Validate message length against configured max
        if (request.message().length() > appConfig.maxMessageLength()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Message must not exceed " + appConfig.maxMessageLength() + " characters",
                    "status", 400,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Check for malicious input
        if (inputSanitizer.isMalicious(request.message())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Request rejected: input detected as malicious",
                    "status", 400,
                    "timestamp", Instant.now().toString()
            ));
        }

        // Sanitize the message
        String sanitizedMessage = inputSanitizer.sanitize(request.message());

        // Get session context
        ConversationHistory history = sessionManager.getHistory(request.sessionId());
        List<ConversationExchange> sessionContext = history.exchanges();

        // Build initial workflow state
        String requestId = UUID.randomUUID().toString();
        WorkflowState initialState = new WorkflowState(
                sanitizedMessage,
                request.sessionId(),
                requestId,
                sessionContext,
                null,
                null,
                null,
                null,
                new HashMap<>()
        );

        // Check if LLM is unavailable - fall back to raw retrieval mode
        if (degradationHandler.isLlmUnavailable()) {
            // Execute only retrieval nodes (skip AnswerGeneration and GroundingEvaluation)
            WorkflowEngine engine = new WorkflowEngine(textWorkflowNodes, appConfig);
            WorkflowState retrievalState = engine.executeUntilRetrieval(initialState);

            long latencyMs = System.currentTimeMillis() - startTime;
            ChatResponse rawResponse = degradationHandler.buildRawRetrievalResponse(
                    retrievalState.retrievedChunks(), latencyMs);

            // Preserve session history during LLM unavailability (Requirement 15.6)
            sessionManager.addExchange(request.sessionId(), new ConversationExchange(
                    sanitizedMessage,
                    rawResponse.answer(),
                    Instant.now()
            ));

            return ResponseEntity.ok()
                    .header("X-Request-ID", requestId)
                    .body(rawResponse);
        }

        // Execute text workflow
        WorkflowEngine engine = new WorkflowEngine(textWorkflowNodes, appConfig);
        WorkflowState finalState = engine.execute(initialState);

        // Measure latency
        long latencyMs = System.currentTimeMillis() - startTime;

        // Build sources from retrieved chunks
        List<SourceReference> sources = buildSources(finalState.retrievedChunks());

        // Determine grounding note
        String groundingNote = finalState.groundingResult() != null
                ? finalState.groundingResult()
                : "unsupported";

        // Build response
        ChatResponse response = new ChatResponse(
                finalState.generatedAnswer(),
                sources,
                groundingNote,
                latencyMs
        );

        // Store the exchange in session history
        sessionManager.addExchange(request.sessionId(), new ConversationExchange(
                sanitizedMessage,
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
