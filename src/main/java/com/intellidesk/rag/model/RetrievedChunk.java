package com.intellidesk.rag.model;

/**
 * A chunk retrieved from the vector store with its similarity score and metadata.
 */
public record RetrievedChunk(
        String content,
        double similarityScore,
        ChunkMetadata metadata
) {}
