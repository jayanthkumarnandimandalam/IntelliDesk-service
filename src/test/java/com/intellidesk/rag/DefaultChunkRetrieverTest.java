package com.intellidesk.rag;

import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.rag.model.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultChunkRetriever")
class DefaultChunkRetrieverTest {

    @Mock
    private VectorStore vectorStore;

    private DefaultChunkRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new DefaultChunkRetriever(vectorStore);
    }

    @Test
    @DisplayName("returns chunks above similarity threshold sorted by score descending")
    void retrievesChunksAboveThreshold() {
        List<ScoredChunk> storeResults = List.of(
                scoredChunk("chunk-a", 0.85),
                scoredChunk("chunk-b", 0.92),
                scoredChunk("chunk-c", 0.78)
        );
        when(vectorStore.search("test query", 5)).thenReturn(storeResults);

        List<RetrievedChunk> results = retriever.retrieve("test query", 5, 0.7);

        assertEquals(3, results.size());
        assertEquals("chunk-b", results.get(0).content());
        assertEquals(0.92, results.get(0).similarityScore());
        assertEquals("chunk-a", results.get(1).content());
        assertEquals(0.85, results.get(1).similarityScore());
        assertEquals("chunk-c", results.get(2).content());
        assertEquals(0.78, results.get(2).similarityScore());
    }

    @Test
    @DisplayName("filters out chunks below similarity threshold")
    void filtersChunksBelowThreshold() {
        List<ScoredChunk> storeResults = List.of(
                scoredChunk("above", 0.8),
                scoredChunk("below-1", 0.5),
                scoredChunk("below-2", 0.3),
                scoredChunk("at-threshold", 0.7)
        );
        when(vectorStore.search("filter query", 5)).thenReturn(storeResults);

        List<RetrievedChunk> results = retriever.retrieve("filter query", 5, 0.7);

        assertEquals(2, results.size());
        assertEquals("above", results.get(0).content());
        assertEquals("at-threshold", results.get(1).content());
    }

    @Test
    @DisplayName("limits results to top-k even when more chunks are above threshold")
    void limitsResultsToTopK() {
        List<ScoredChunk> storeResults = List.of(
                scoredChunk("chunk-1", 0.95),
                scoredChunk("chunk-2", 0.90),
                scoredChunk("chunk-3", 0.85),
                scoredChunk("chunk-4", 0.80),
                scoredChunk("chunk-5", 0.75)
        );
        when(vectorStore.search("limit query", 3)).thenReturn(storeResults);

        List<RetrievedChunk> results = retriever.retrieve("limit query", 3, 0.7);

        assertEquals(3, results.size());
        assertEquals("chunk-1", results.get(0).content());
        assertEquals("chunk-2", results.get(1).content());
        assertEquals("chunk-3", results.get(2).content());
    }

    @Test
    @DisplayName("returns empty list when vector store returns no results")
    void returnsEmptyWhenNoResults() {
        when(vectorStore.search("unknown query", 5)).thenReturn(List.of());

        List<RetrievedChunk> results = retriever.retrieve("unknown query", 5, 0.7);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("returns empty list when all results are below threshold")
    void returnsEmptyWhenAllBelowThreshold() {
        List<ScoredChunk> storeResults = List.of(
                scoredChunk("low-1", 0.4),
                scoredChunk("low-2", 0.55),
                scoredChunk("low-3", 0.69)
        );
        when(vectorStore.search("low query", 5)).thenReturn(storeResults);

        List<RetrievedChunk> results = retriever.retrieve("low query", 5, 0.7);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("preserves chunk metadata in retrieved results")
    void preservesMetadata() {
        ChunkMetadata metadata = new ChunkMetadata(
                "guide.md", "docs/guide.md", ".md",
                Instant.parse("2024-01-15T10:30:00Z"), "Getting Started"
        );
        ScoredChunk scored = new ScoredChunk("content here", 0.88, metadata);
        when(vectorStore.search("meta query", 5)).thenReturn(List.of(scored));

        List<RetrievedChunk> results = retriever.retrieve("meta query", 5, 0.7);

        assertEquals(1, results.size());
        RetrievedChunk result = results.get(0);
        assertEquals("content here", result.content());
        assertEquals(0.88, result.similarityScore());
        assertEquals("guide.md", result.metadata().fileName());
        assertEquals("docs/guide.md", result.metadata().filePath());
        assertEquals(".md", result.metadata().fileType());
        assertEquals("Getting Started", result.metadata().sectionTitle());
    }

    private ScoredChunk scoredChunk(String content, double score) {
        ChunkMetadata metadata = new ChunkMetadata(
                "test.md", "path/test.md", ".md",
                Instant.now(), "Section"
        );
        return new ScoredChunk(content, score, metadata);
    }
}
