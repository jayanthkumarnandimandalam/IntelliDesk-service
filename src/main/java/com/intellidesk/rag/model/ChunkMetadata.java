package com.intellidesk.rag.model;

import java.time.Instant;

/**
 * Metadata associated with a stored chunk in the vector store.
 */
public record ChunkMetadata(
        String fileName,
        String filePath,
        String fileType,
        Instant ingestionTimestamp,
        String sectionTitle
) {}
