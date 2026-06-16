package com.intellidesk.rag.model;

/**
 * A chunk returned from the vector store with its computed similarity score.
 * This is the raw result from the vector store before any filtering is applied.
 */
public record ScoredChunk(
        String content,
        double similarityScore,
        ChunkMetadata metadata
) {}
