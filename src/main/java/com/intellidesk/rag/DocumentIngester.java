package com.intellidesk.rag;

import com.intellidesk.rag.model.IngestionConfig;
import com.intellidesk.rag.model.IngestionResult;

import java.nio.file.Path;

/**
 * Interface for ingesting documents into the vector store.
 */
public interface DocumentIngester {

    /**
     * Ingests all supported documents from the given directory.
     * Splits documents into chunks, generates embeddings, and stores them in the vector store.
     *
     * @param knowledgeBaseDir path to the knowledge base directory
     * @param config ingestion configuration (chunk size, overlap, supported extensions)
     * @return the result of the ingestion operation
     */
    IngestionResult ingest(Path knowledgeBaseDir, IngestionConfig config);
}
