package com.intellidesk.rag.model;

import java.util.List;

/**
 * Result of a document ingestion operation.
 */
public record IngestionResult(
        int documentCount,
        int chunkCount,
        String status,
        List<String> failedDocuments
) {}
