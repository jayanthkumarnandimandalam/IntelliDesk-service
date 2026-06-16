package com.intellidesk.api;

import com.intellidesk.config.AppConfig;
import com.intellidesk.evaluation.EvaluationException;
import com.intellidesk.evaluation.EvaluationRunner;
import com.intellidesk.evaluation.model.EvaluationResult;
import com.intellidesk.evaluation.model.ExampleResult;
import com.intellidesk.evaluation.model.MetricSummary;
import com.intellidesk.rag.InMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvaluationControllerTest {

    private EvaluationRunner evaluationRunner;
    private InMemoryVectorStore vectorStore;
    private AppConfig appConfig;
    private EvaluationController controller;

    @BeforeEach
    void setUp() {
        evaluationRunner = mock(EvaluationRunner.class);
        vectorStore = mock(InMemoryVectorStore.class);
        appConfig = createAppConfig(120);
        controller = new EvaluationController(evaluationRunner, vectorStore, appConfig);
    }

    @Test
    void evaluate_success_returnsOkWithResult() {
        when(vectorStore.size()).thenReturn(100);
        EvaluationResult expectedResult = createSuccessfulResult();
        when(evaluationRunner.execute()).thenReturn(expectedResult);

        ResponseEntity<?> response = controller.evaluate();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        EvaluationResult result = (EvaluationResult) response.getBody();
        assertNotNull(result);
        assertTrue(result.overallPass());
        assertEquals(0.85, result.metricSummary().retrievalPrecision());
    }

    @Test
    void evaluate_noIngestion_returns409() {
        when(vectorStore.size()).thenReturn(0);

        ResponseEntity<?> response = controller.evaluate();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(409, body.get("status"));
        assertTrue(body.get("error").toString().contains("Ingestion must be completed"));
    }

    @Test
    void evaluate_alreadyRunning_returns409() throws Exception {
        when(vectorStore.size()).thenReturn(100);

        // Simulate evaluation in progress by having the runner block
        when(evaluationRunner.execute()).thenAnswer(invocation -> {
            Thread.sleep(5000);
            return createSuccessfulResult();
        });

        // Start first evaluation in a separate thread
        Thread firstEval = new Thread(() -> controller.evaluate());
        firstEval.start();

        // Give the first evaluation time to acquire the lock
        Thread.sleep(100);

        // Second evaluation should get 409
        ResponseEntity<?> response = controller.evaluate();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(409, body.get("status"));
        assertTrue(body.get("error").toString().contains("already in progress"));

        firstEval.interrupt();
        firstEval.join(2000);
    }

    @Test
    void evaluate_datasetMissing_returns404() {
        when(vectorStore.size()).thenReturn(100);
        when(evaluationRunner.execute())
                .thenThrow(new EvaluationException("Evaluation dataset not found", 404));

        ResponseEntity<?> response = controller.evaluate();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(404, body.get("status"));
        assertTrue(body.get("error").toString().contains("dataset"));
    }

    @Test
    void evaluate_timeout_returns504() {
        // Use a very short timeout to trigger timeout behavior
        appConfig = createAppConfig(1);
        controller = new EvaluationController(evaluationRunner, vectorStore, appConfig);
        when(vectorStore.size()).thenReturn(100);
        when(evaluationRunner.execute()).thenAnswer(invocation -> {
            Thread.sleep(5000); // Exceeds the 1-second timeout
            return createSuccessfulResult();
        });

        ResponseEntity<?> response = controller.evaluate();

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(504, body.get("status"));
        assertTrue(body.get("error").toString().contains("timed out"));
    }

    @Test
    void evaluate_releasesLockAfterSuccess() {
        when(vectorStore.size()).thenReturn(100);
        when(evaluationRunner.execute()).thenReturn(createSuccessfulResult());

        // First call succeeds
        ResponseEntity<?> response1 = controller.evaluate();
        assertEquals(HttpStatus.OK, response1.getStatusCode());

        // Second call should also succeed (lock released)
        ResponseEntity<?> response2 = controller.evaluate();
        assertEquals(HttpStatus.OK, response2.getStatusCode());
    }

    @Test
    void evaluate_releasesLockAfterFailure() {
        when(vectorStore.size()).thenReturn(100);
        when(evaluationRunner.execute())
                .thenThrow(new EvaluationException("Dataset not found", 404))
                .thenReturn(createSuccessfulResult());

        // First call fails
        ResponseEntity<?> response1 = controller.evaluate();
        assertEquals(HttpStatus.NOT_FOUND, response1.getStatusCode());

        // Second call should succeed (lock released)
        ResponseEntity<?> response2 = controller.evaluate();
        assertEquals(HttpStatus.OK, response2.getStatusCode());
    }

    @Test
    void evaluate_releasesLockAfterTimeout() {
        appConfig = createAppConfig(1);
        controller = new EvaluationController(evaluationRunner, vectorStore, appConfig);
        when(vectorStore.size()).thenReturn(100);

        // First call times out
        when(evaluationRunner.execute()).thenAnswer(invocation -> {
            Thread.sleep(5000);
            return createSuccessfulResult();
        });
        ResponseEntity<?> response1 = controller.evaluate();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response1.getStatusCode());

        // Second call should not get 409 (lock released)
        when(evaluationRunner.execute()).thenReturn(createSuccessfulResult());
        appConfig = createAppConfig(120);
        controller = new EvaluationController(evaluationRunner, vectorStore, appConfig);
        ResponseEntity<?> response2 = controller.evaluate();
        assertEquals(HttpStatus.OK, response2.getStatusCode());
    }

    private EvaluationResult createSuccessfulResult() {
        MetricSummary metrics = new MetricSummary(0.85, 0.90, 0.88, 0.95);
        List<ExampleResult> examples = List.of(
                new ExampleResult("How to reset password?", "answerable", "answerable", true)
        );
        return new EvaluationResult(metrics, true, "./data/evaluation/report.json", examples);
    }

    private static AppConfig createAppConfig(int evaluationTimeoutSec) {
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
                evaluationTimeoutSec,
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
