package com.intellidesk.rag.model;

import java.util.Set;

/**
 * Configuration for the document ingestion pipeline.
 */
public record IngestionConfig(
        int chunkSize,
        int chunkOverlap,
        Set<String> supportedExtensions
) {}
