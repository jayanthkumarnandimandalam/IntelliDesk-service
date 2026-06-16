package com.intellidesk.api;

import com.intellidesk.config.AppConfig;
import com.intellidesk.rag.DefaultDocumentIngester;
import com.intellidesk.rag.IngestionException;
import com.intellidesk.rag.model.IngestionConfig;
import com.intellidesk.rag.model.IngestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestionControllerTest {

    private DefaultDocumentIngester documentIngester;
    private AppConfig appConfig;
    private IngestionController controller;

    @BeforeEach
    void setUp() {
        documentIngester = mock(DefaultDocumentIngester.class);
        appConfig = createAppConfig();
        controller = new IngestionController(documentIngester, appConfig);
    }

    @Test
    void ingest_success_returnsOkWithResult() {
        IngestionResult expectedResult = new IngestionResult(5, 42, "completed", List.of());
        when(documentIngester.ingest(any(Path.class), any(IngestionConfig.class)))
                .thenReturn(expectedResult);

        ResponseEntity<?> response = controller.ingest();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        IngestionResult result = (IngestionResult) response.getBody();
        assertNotNull(result);
        assertEquals(5, result.documentCount());
        assertEquals(42, result.chunkCount());
        assertEquals("completed", result.status());
        assertTrue(result.failedDocuments().isEmpty());
    }

    @Test
    void ingest_success_passesConfiguredKnowledgeBaseDirAndChunkSettings() {
        IngestionResult expectedResult = new IngestionResult(3, 20, "completed", List.of());
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<IngestionConfig> configCaptor = ArgumentCaptor.forClass(IngestionConfig.class);
        when(documentIngester.ingest(pathCaptor.capture(), configCaptor.capture()))
                .thenReturn(expectedResult);

        controller.ingest();

        assertEquals(Path.of("./data/knowledge-base"), pathCaptor.getValue());
        IngestionConfig capturedConfig = configCaptor.getValue();
        assertEquals(512, capturedConfig.chunkSize());
        assertEquals(50, capturedConfig.chunkOverlap());
        assertEquals(Set.of(".md", ".txt", ".pdf"), capturedConfig.supportedExtensions());
    }

    @Test
    void ingest_completedWithErrors_returnsOkWithFailedDocuments() {
        IngestionResult expectedResult = new IngestionResult(
                4, 30, "completed_with_errors", List.of("corrupted.pdf"));
        when(documentIngester.ingest(any(Path.class), any(IngestionConfig.class)))
                .thenReturn(expectedResult);

        ResponseEntity<?> response = controller.ingest();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        IngestionResult result = (IngestionResult) response.getBody();
        assertNotNull(result);
        assertEquals("completed_with_errors", result.status());
        assertEquals(List.of("corrupted.pdf"), result.failedDocuments());
    }

    @Test
    void ingest_directoryNotFound_returns404() {
        when(documentIngester.ingest(any(Path.class), any(IngestionConfig.class)))
                .thenThrow(new IngestionException("Knowledge base directory does not exist: ./data/knowledge-base", 404));

        ResponseEntity<?> response = controller.ingest();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(404, body.get("status"));
        assertTrue(body.get("error").toString().contains("does not exist"));
    }

    @Test
    void ingest_alreadyInProgress_returns409() {
        when(documentIngester.ingest(any(Path.class), any(IngestionConfig.class)))
                .thenThrow(new IngestionException("Ingestion already in progress", 409));

        ResponseEntity<?> response = controller.ingest();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(409, body.get("status"));
        assertTrue(body.get("error").toString().contains("already in progress"));
    }

    @Test
    void ingest_vectorStoreUnavailable_returns503() {
        when(documentIngester.ingest(any(Path.class), any(IngestionConfig.class)))
                .thenThrow(new IngestionException("Vector store is not reachable", 503));

        ResponseEntity<?> response = controller.ingest();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(503, body.get("status"));
        assertTrue(body.get("error").toString().contains("Vector store"));
    }

    @Test
    void ingest_invalidConfig_returns400() {
        when(documentIngester.ingest(any(Path.class), any(IngestionConfig.class)))
                .thenThrow(new IllegalArgumentException("chunkSize must be between 64 and 2048, got: 10"));

        ResponseEntity<?> response = controller.ingest();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
        assertTrue(body.get("error").toString().contains("chunkSize"));
    }

    private static AppConfig createAppConfig() {
        return new AppConfig(
                5,          // topK
                0.7,        // similarityThreshold
                4000,       // maxMessageLength
                10,         // contextWindowSize
                30,         // sessionTimeoutMinutes
                20,         // rateLimitPerMinute
                10,         // maxAudioSizeMb
                60,         // maxAudioDurationSec
                30,         // nodeTimeoutSeconds
                512,        // chunkSize
                50,         // chunkOverlap
                0.7,        // evaluationThreshold
                120,        // evaluationTimeoutSec
                5,          // healthCheckTimeoutSec
                5,          // cbFailureThreshold
                30,         // cbCooldownSeconds
                "local",    // activeProfile
                "gpt-4o-mini",  // llmModel
                "text-embedding-3-small", // embeddingModel
                "http://localhost:8000",   // vectorStoreUrl
                "http://localhost:9000",   // sttUrl
                "./data/knowledge-base",  // knowledgeBaseDir
                "./data/evaluation/dataset.json", // evaluationDatasetPath
                "./data/evaluation/report.json",  // evaluationReportPath
                30,         // llmTimeoutSeconds
                "http://localhost:5173"    // corsAllowedOrigins
        );
    }
}
