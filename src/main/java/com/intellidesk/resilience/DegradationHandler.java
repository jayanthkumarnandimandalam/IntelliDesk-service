package com.intellidesk.resilience;

import com.intellidesk.api.dto.ChatResponse;
import com.intellidesk.api.dto.SourceReference;
import com.intellidesk.rag.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that checks circuit breaker states for each external dependency and
 * provides degradation logic when services are unavailable.
 *
 * Degradation behavior:
 * - STT unavailable (OPEN): Voice endpoint returns 503, text chat unaffected
 * - LLM unavailable (OPEN): Return top-k chunks as raw context in answer field,
 *   grounding_note set to "raw retrieval mode"
 * - VectorStore unavailable (OPEN): All retrieval-dependent endpoints return 503
 *
 * @see <a href="requirements.md">Requirements 15.1, 15.2, 15.3, 15.6</a>
 */
@Service
public class DegradationHandler {

    private static final Logger logger = LoggerFactory.getLogger(DegradationHandler.class);

    public static final String RAW_RETRIEVAL_MODE = "raw retrieval mode";

    private final CircuitBreaker llmCircuitBreaker;
    private final CircuitBreaker vectorStoreCircuitBreaker;
    private final CircuitBreaker sttCircuitBreaker;

    public DegradationHandler(
            @Qualifier("llmCircuitBreaker") CircuitBreaker llmCircuitBreaker,
            @Qualifier("vectorStoreCircuitBreaker") CircuitBreaker vectorStoreCircuitBreaker,
            @Qualifier("sttCircuitBreaker") CircuitBreaker sttCircuitBreaker) {
        this.llmCircuitBreaker = llmCircuitBreaker;
        this.vectorStoreCircuitBreaker = vectorStoreCircuitBreaker;
        this.sttCircuitBreaker = sttCircuitBreaker;
    }

    /**
     * Checks whether the STT service is unavailable (circuit breaker OPEN).
     * When true, voice endpoint should return 503.
     */
    public boolean isSttUnavailable() {
        return sttCircuitBreaker.getState() == CircuitState.OPEN;
    }

    /**
     * Checks whether the LLM service is unavailable (circuit breaker OPEN).
     * When true, the workflow should fall back to raw retrieval mode.
     */
    public boolean isLlmUnavailable() {
        return llmCircuitBreaker.getState() == CircuitState.OPEN;
    }

    /**
     * Checks whether the Vector Store is unavailable (circuit breaker OPEN).
     * When true, all retrieval-dependent endpoints should return 503.
     */
    public boolean isVectorStoreUnavailable() {
        return vectorStoreCircuitBreaker.getState() == CircuitState.OPEN;
    }

    /**
     * Builds a raw retrieval mode response when the LLM is unavailable.
     * Concatenates the content of retrieved chunks as the answer field and
     * sets grounding_note to "raw retrieval mode".
     *
     * Session history is preserved during LLM unavailability (Requirement 15.6).
     *
     * @param retrievedChunks the top-k chunks retrieved from the vector store
     * @param latencyMs       the request processing latency
     * @return a ChatResponse with raw chunks as the answer
     */
    public ChatResponse buildRawRetrievalResponse(List<RetrievedChunk> retrievedChunks, long latencyMs) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            logger.warn("LLM unavailable and no chunks retrieved - returning empty raw retrieval response");
            return new ChatResponse(
                    "No relevant knowledge base content found.",
                    Collections.emptyList(),
                    RAW_RETRIEVAL_MODE,
                    latencyMs
            );
        }

        // Concatenate chunk content as the raw answer
        String rawAnswer = retrievedChunks.stream()
                .map(RetrievedChunk::content)
                .collect(Collectors.joining("\n\n"));

        // Build source references from chunk metadata
        List<SourceReference> sources = retrievedChunks.stream()
                .filter(chunk -> chunk.metadata() != null)
                .map(chunk -> new SourceReference(
                        chunk.metadata().fileName(),
                        chunk.metadata().sectionTitle()
                ))
                .toList();

        logger.info("Returning raw retrieval mode response with {} chunks", retrievedChunks.size());

        return new ChatResponse(
                rawAnswer,
                sources,
                RAW_RETRIEVAL_MODE,
                latencyMs
        );
    }
}
