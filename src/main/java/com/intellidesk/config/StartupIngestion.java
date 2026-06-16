package com.intellidesk.config;

import com.intellidesk.rag.DefaultDocumentIngester;
import com.intellidesk.rag.model.IngestionConfig;
import com.intellidesk.rag.model.IngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Startup listener that automatically ingests knowledge base documents
 * into the in-memory vector store when the application starts.
 * Ensures the RAG pipeline has content available immediately.
 */
@Component
public class StartupIngestion {

    private static final Logger logger = LoggerFactory.getLogger(StartupIngestion.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".md", ".txt", ".pdf");

    private final DefaultDocumentIngester documentIngester;
    private final AppConfig appConfig;

    public StartupIngestion(DefaultDocumentIngester documentIngester, AppConfig appConfig) {
        this.documentIngester = documentIngester;
        this.appConfig = appConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestKnowledgeBase() {
        String knowledgeBaseDir = appConfig.knowledgeBaseDir();
        Path kbPath = Paths.get(knowledgeBaseDir);

        logger.info("Starting knowledge base ingestion from: {}", kbPath.toAbsolutePath());

        try {
            IngestionConfig config = new IngestionConfig(
                    appConfig.chunkSize(),
                    appConfig.chunkOverlap(),
                    SUPPORTED_EXTENSIONS
            );

            IngestionResult result = documentIngester.ingest(kbPath, config);

            logger.info("Knowledge base ingestion complete: {} documents, {} chunks, status={}",
                    result.documentCount(), result.chunkCount(), result.status());

            if (!result.failedDocuments().isEmpty()) {
                logger.warn("Failed to ingest documents: {}", result.failedDocuments());
            }
        } catch (Exception e) {
            logger.error("Knowledge base ingestion failed: {}. The application will continue but RAG responses may be empty.", e.getMessage());
        }
    }
}
