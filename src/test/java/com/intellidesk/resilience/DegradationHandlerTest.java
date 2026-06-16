package com.intellidesk.resilience;

import com.intellidesk.api.dto.ChatResponse;
import com.intellidesk.api.dto.SourceReference;
import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DegradationHandler verifying:
 * - LLM down → raw retrieval mode response
 * - STT down → 503 on voice, text chat still works
 * - VectorStore down → 503 on both endpoints
 */
@ExtendWith(MockitoExtension.class)
class DegradationHandlerTest {

    @Mock
    private CircuitBreaker llmCircuitBreaker;

    @Mock
    private CircuitBreaker vectorStoreCircuitBreaker;

    @Mock
    private CircuitBreaker sttCircuitBreaker;

    private DegradationHandler degradationHandler;

    @BeforeEach
    void setUp() {
        degradationHandler = new DegradationHandler(
                llmCircuitBreaker, vectorStoreCircuitBreaker, sttCircuitBreaker);
    }

    @Nested
    @DisplayName("STT Unavailable - Voice 503, Text Chat Still Works")
    class SttUnavailable {

        @Test
        @DisplayName("isSttUnavailable returns true when STT circuit breaker is OPEN")
        void sttUnavailable_whenCircuitOpen() {
            when(sttCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);

            assertTrue(degradationHandler.isSttUnavailable());
        }

        @Test
        @DisplayName("isSttUnavailable returns false when STT circuit breaker is CLOSED")
        void sttAvailable_whenCircuitClosed() {
            when(sttCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

            assertFalse(degradationHandler.isSttUnavailable());
        }

        @Test
        @DisplayName("Text chat is not affected when STT is down")
        void textChatNotAffectedBySttDown() {
            when(sttCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);
            when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
            when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

            // STT is down but LLM and VectorStore are up
            assertTrue(degradationHandler.isSttUnavailable());
            assertFalse(degradationHandler.isLlmUnavailable());
            assertFalse(degradationHandler.isVectorStoreUnavailable());
        }
    }

    @Nested
    @DisplayName("LLM Unavailable - Raw Retrieval Mode")
    class LlmUnavailable {

        @Test
        @DisplayName("isLlmUnavailable returns true when LLM circuit breaker is OPEN")
        void llmUnavailable_whenCircuitOpen() {
            when(llmCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);

            assertTrue(degradationHandler.isLlmUnavailable());
        }

        @Test
        @DisplayName("isLlmUnavailable returns false when LLM circuit breaker is CLOSED")
        void llmAvailable_whenCircuitClosed() {
            when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

            assertFalse(degradationHandler.isLlmUnavailable());
        }

        @Test
        @DisplayName("Raw retrieval response concatenates chunk content as answer")
        void rawRetrievalResponse_concatenatesChunks() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk("Chunk 1 content about password resets.", 0.85,
                            new ChunkMetadata("password-guide.md", "/docs/password-guide.md",
                                    ".md", Instant.now(), "Password Reset")),
                    new RetrievedChunk("Chunk 2 content about account recovery.", 0.80,
                            new ChunkMetadata("account-recovery.md", "/docs/account-recovery.md",
                                    ".md", Instant.now(), "Account Recovery"))
            );

            ChatResponse response = degradationHandler.buildRawRetrievalResponse(chunks, 250L);

            assertNotNull(response);
            assertTrue(response.answer().contains("Chunk 1 content about password resets."));
            assertTrue(response.answer().contains("Chunk 2 content about account recovery."));
            assertEquals(DegradationHandler.RAW_RETRIEVAL_MODE, response.groundingNote());
            assertEquals(250L, response.latencyMs());
        }

        @Test
        @DisplayName("Raw retrieval response includes source references from chunk metadata")
        void rawRetrievalResponse_includesSources() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk("Content 1", 0.85,
                            new ChunkMetadata("guide.md", "/docs/guide.md",
                                    ".md", Instant.now(), "Section A")),
                    new RetrievedChunk("Content 2", 0.80,
                            new ChunkMetadata("faq.md", "/docs/faq.md",
                                    ".md", Instant.now(), "FAQ"))
            );

            ChatResponse response = degradationHandler.buildRawRetrievalResponse(chunks, 100L);

            assertEquals(2, response.sources().size());
            assertEquals("guide.md", response.sources().get(0).documentTitle());
            assertEquals("Section A", response.sources().get(0).section());
            assertEquals("faq.md", response.sources().get(1).documentTitle());
            assertEquals("FAQ", response.sources().get(1).section());
        }

        @Test
        @DisplayName("Raw retrieval response with no chunks returns empty message")
        void rawRetrievalResponse_noChunks() {
            ChatResponse response = degradationHandler.buildRawRetrievalResponse(
                    Collections.emptyList(), 50L);

            assertEquals("No relevant knowledge base content found.", response.answer());
            assertTrue(response.sources().isEmpty());
            assertEquals(DegradationHandler.RAW_RETRIEVAL_MODE, response.groundingNote());
        }

        @Test
        @DisplayName("Raw retrieval response with null chunks returns empty message")
        void rawRetrievalResponse_nullChunks() {
            ChatResponse response = degradationHandler.buildRawRetrievalResponse(null, 50L);

            assertEquals("No relevant knowledge base content found.", response.answer());
            assertTrue(response.sources().isEmpty());
            assertEquals(DegradationHandler.RAW_RETRIEVAL_MODE, response.groundingNote());
        }

        @Test
        @DisplayName("Grounding note is always 'raw retrieval mode' when LLM is down")
        void rawRetrievalResponse_groundingNoteIsRawRetrievalMode() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk("Some content", 0.9,
                            new ChunkMetadata("file.md", "/file.md", ".md", Instant.now(), "Title"))
            );

            ChatResponse response = degradationHandler.buildRawRetrievalResponse(chunks, 100L);

            assertEquals("raw retrieval mode", response.groundingNote());
        }
    }

    @Nested
    @DisplayName("VectorStore Unavailable - 503 on Both Endpoints")
    class VectorStoreUnavailable {

        @Test
        @DisplayName("isVectorStoreUnavailable returns true when VectorStore circuit breaker is OPEN")
        void vectorStoreUnavailable_whenCircuitOpen() {
            when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);

            assertTrue(degradationHandler.isVectorStoreUnavailable());
        }

        @Test
        @DisplayName("isVectorStoreUnavailable returns false when VectorStore circuit breaker is CLOSED")
        void vectorStoreAvailable_whenCircuitClosed() {
            when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

            assertFalse(degradationHandler.isVectorStoreUnavailable());
        }

        @Test
        @DisplayName("Both text and voice are affected when VectorStore is down")
        void bothEndpointsAffected_whenVectorStoreDown() {
            when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);
            when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
            when(sttCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

            // VectorStore is down, both endpoints should be blocked
            assertTrue(degradationHandler.isVectorStoreUnavailable());
            // LLM and STT are fine
            assertFalse(degradationHandler.isLlmUnavailable());
            assertFalse(degradationHandler.isSttUnavailable());
        }
    }

    @Nested
    @DisplayName("Session History Preservation During LLM Unavailability")
    class SessionHistoryPreservation {

        @Test
        @DisplayName("Raw retrieval response preserves structure for session history storage")
        void rawRetrievalResponse_preservesResponseStructure() {
            // When LLM is unavailable, the response should have a valid answer field
            // that can be stored in session history
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk("Relevant content", 0.85,
                            new ChunkMetadata("doc.md", "/doc.md", ".md", Instant.now(), "Section"))
            );

            ChatResponse response = degradationHandler.buildRawRetrievalResponse(chunks, 100L);

            // Answer is non-null and non-empty - suitable for session history
            assertNotNull(response.answer());
            assertFalse(response.answer().isBlank());
            // Response structure is unchanged (same fields as normal ChatResponse)
            assertNotNull(response.sources());
            assertNotNull(response.groundingNote());
            assertTrue(response.latencyMs() > 0);
        }
    }
}
