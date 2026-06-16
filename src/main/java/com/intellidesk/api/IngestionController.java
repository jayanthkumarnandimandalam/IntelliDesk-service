package com.intellidesk.api;

import com.intellidesk.config.AppConfig;
import com.intellidesk.rag.DefaultDocumentIngester;
import com.intellidesk.rag.IngestionException;
import com.intellidesk.rag.model.IngestionConfig;
import com.intellidesk.rag.model.IngestionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for document ingestion operations.
 * Triggers ingestion of documents from the configured knowledge base directory.
 */
@RestController
@RequestMapping("/api")
public class IngestionController {

    private final DefaultDocumentIngester documentIngester;
    private final AppConfig appConfig;

    public IngestionController(DefaultDocumentIngester documentIngester, AppConfig appConfig) {
        this.documentIngester = documentIngester;
        this.appConfig = appConfig;
    }

    /**
     * POST /api/ingest - Trigger document ingestion from the configured knowledge base directory.
     *
     * @return 200 with IngestionResult on success,
     *         400 if configuration is invalid,
     *         404 if KB directory is missing or empty,
     *         409 if ingestion is already in progress,
     *         503 if vector store is unavailable
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> ingest() {
        try {
            Path knowledgeBaseDir = Path.of(appConfig.knowledgeBaseDir());
            IngestionConfig config = new IngestionConfig(
                    appConfig.chunkSize(),
                    appConfig.chunkOverlap(),
                    Set.of(".md", ".txt", ".pdf")
            );

            IngestionResult result = documentIngester.ingest(knowledgeBaseDir, config);
            return ResponseEntity.ok(result);

        } catch (IngestionException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", e.getMessage(), "status", e.getStatusCode()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "status", 400));
        }
    }
}
