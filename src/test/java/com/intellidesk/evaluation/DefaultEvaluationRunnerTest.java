package com.intellidesk.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellidesk.config.AppConfig;
import com.intellidesk.evaluation.model.EvaluationResult;
import com.intellidesk.evaluation.model.ExampleResult;
import com.intellidesk.evaluation.model.MetricSummary;
import com.intellidesk.rag.ChunkRetriever;
import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultEvaluationRunner}.
 * Verifies metric computation logic with a mock dataset.
 */
class DefaultEvaluationRunnerTest {

    @TempDir
    Path tempDir;

    private ChunkRetriever chunkRetriever;
    private ObjectMapper objectMapper;
    private DefaultEvaluationRunner runner;
    private Path datasetPath;
    private Path reportPath;

    @BeforeEach
    void setUp() throws IOException {
        chunkRetriever = mock(ChunkRetriever.class);
        objectMapper = new ObjectMapper();

        datasetPath = tempDir.resolve("dataset.json");
        reportPath = tempDir.resolve("report.json");

        AppConfig appConfig = new AppConfig(
                5,      // topK
                0.7,    // similarityThreshold
                4000,   // maxMessageLength
                10,     // contextWindowSize
                30,     // sessionTimeoutMinutes
                20,     // rateLimitPerMinute
                10,     // maxAudioSizeMb
                60,     // maxAudioDurationSec
                30,     // nodeTimeoutSeconds
                512,    // chunkSize
                50,     // chunkOverlap
                0.7,    // evaluationThreshold
                120,    // evaluationTimeoutSec
                5,      // healthCheckTimeoutSec
                5,      // cbFailureThreshold
                30,     // cbCooldownSeconds
                "local", // activeProfile
                "gpt-4o-mini",  // llmModel
                "text-embedding-3-small", // embeddingModel
                "http://localhost:8000",   // vectorStoreUrl
                "http://localhost:9000",   // sttUrl
                "./data/knowledge-base",  // knowledgeBaseDir
                datasetPath.toString(),   // evaluationDatasetPath
                reportPath.toString(),    // evaluationReportPath
                30,     // llmTimeoutSeconds
                "http://localhost:5173"    // corsAllowedOrigins
        );

        runner = new DefaultEvaluationRunner(
                appConfig, chunkRetriever, objectMapper, () -> true);
    }

    @Test
    void execute_withMockDataset_computesMetricsCorrectly() throws IOException {
        // Create a dataset with 10 examples: 4 answerable, 3 follow-up, 3 unsupported
        String dataset = """
                [
                  {"question": "How do I reset my password?", "expectedCategory": "answerable", "expectedAnswerKeywords": ["password", "reset"]},
                  {"question": "How to connect to VPN?", "expectedCategory": "answerable", "expectedAnswerKeywords": ["vpn", "connect"]},
                  {"question": "What are the printer setup steps?", "expectedCategory": "answerable", "expectedAnswerKeywords": ["printer", "setup"]},
                  {"question": "How to install Office 365?", "expectedCategory": "answerable", "expectedAnswerKeywords": ["office", "install"]},
                  {"question": "Can you tell me more about that?", "expectedCategory": "follow-up", "expectedAnswerKeywords": ["more"]},
                  {"question": "What about the second option?", "expectedCategory": "follow-up", "expectedAnswerKeywords": ["option"]},
                  {"question": "And what about the cost?", "expectedCategory": "follow-up", "expectedAnswerKeywords": ["cost"]},
                  {"question": "What is the meaning of life?", "expectedCategory": "unsupported", "expectedAnswerKeywords": []},
                  {"question": "Tell me a joke", "expectedCategory": "unsupported", "expectedAnswerKeywords": []},
                  {"question": "What is quantum physics?", "expectedCategory": "unsupported", "expectedAnswerKeywords": []}
                ]
                """;
        Files.writeString(datasetPath, dataset);

        ChunkMetadata metadata = new ChunkMetadata(
                "guide.md", "guide.md", ".md", Instant.now(), "IT Guide");

        // Answerable questions return relevant chunks with matching keywords
        when(chunkRetriever.retrieve(eq("How do I reset my password?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("To reset your password, go to settings", 0.9, metadata)));
        when(chunkRetriever.retrieve(eq("How to connect to VPN?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("Connect to VPN using the client", 0.85, metadata)));
        when(chunkRetriever.retrieve(eq("What are the printer setup steps?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("Printer setup requires drivers", 0.8, metadata)));
        when(chunkRetriever.retrieve(eq("How to install Office 365?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("Install Office 365 from the portal", 0.88, metadata)));

        // Follow-up questions return relevant chunks
        when(chunkRetriever.retrieve(eq("Can you tell me more about that?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("More details about the feature", 0.75, metadata)));
        when(chunkRetriever.retrieve(eq("What about the second option?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("The second option is available", 0.78, metadata)));
        when(chunkRetriever.retrieve(eq("And what about the cost?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("The cost is documented here", 0.72, metadata)));

        // Unsupported questions return no chunks
        when(chunkRetriever.retrieve(eq("What is the meaning of life?"), anyInt(), anyDouble()))
                .thenReturn(List.of());
        when(chunkRetriever.retrieve(eq("Tell me a joke"), anyInt(), anyDouble()))
                .thenReturn(List.of());
        when(chunkRetriever.retrieve(eq("What is quantum physics?"), anyInt(), anyDouble()))
                .thenReturn(List.of());

        EvaluationResult result = runner.execute();

        // Verify metrics
        MetricSummary metrics = result.metricSummary();

        // Retrieval precision: 7 out of 10 queries returned chunks
        assertEquals(0.7, metrics.retrievalPrecision(), 0.001);

        // Answer relevance: answerable (4 have matching keywords) + follow-up (3 have matching keywords)
        // = 7 out of 10
        assertEquals(0.7, metrics.answerRelevance(), 0.001);

        // Grounding accuracy: all categories correctly identified (10/10)
        assertEquals(1.0, metrics.groundingAccuracy(), 0.001);

        // Unsupported detection rate: 3 unsupported correctly detected out of 3
        assertEquals(1.0, metrics.unsupportedDetectionRate(), 0.001);

        // Overall pass: all metrics >= 0.7
        assertTrue(result.overallPass());

        // Per-example results
        assertEquals(10, result.perExampleResults().size());

        // Verify report was persisted
        assertTrue(Files.exists(reportPath));
        String reportContent = Files.readString(reportPath);
        assertFalse(reportContent.isEmpty());
    }

    @Test
    void execute_withLowMetrics_overallPassIsFalse() throws IOException {
        // Dataset with 10 examples but retrieval fails for many
        String dataset = """
                [
                  {"question": "Q1", "expectedCategory": "answerable", "expectedAnswerKeywords": ["answer"]},
                  {"question": "Q2", "expectedCategory": "answerable", "expectedAnswerKeywords": ["answer"]},
                  {"question": "Q3", "expectedCategory": "answerable", "expectedAnswerKeywords": ["answer"]},
                  {"question": "Q4", "expectedCategory": "answerable", "expectedAnswerKeywords": ["answer"]},
                  {"question": "Q5", "expectedCategory": "follow-up", "expectedAnswerKeywords": ["follow"]},
                  {"question": "Q6", "expectedCategory": "follow-up", "expectedAnswerKeywords": ["follow"]},
                  {"question": "Q7", "expectedCategory": "follow-up", "expectedAnswerKeywords": ["follow"]},
                  {"question": "Q8", "expectedCategory": "unsupported", "expectedAnswerKeywords": []},
                  {"question": "Q9", "expectedCategory": "unsupported", "expectedAnswerKeywords": []},
                  {"question": "Q10", "expectedCategory": "unsupported", "expectedAnswerKeywords": []}
                ]
                """;
        Files.writeString(datasetPath, dataset);

        // Return empty results for all queries - everything gets classified as unsupported
        when(chunkRetriever.retrieve(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        EvaluationResult result = runner.execute();

        // Retrieval precision: 0/10 = 0.0
        assertEquals(0.0, result.metricSummary().retrievalPrecision(), 0.001);

        // Grounding accuracy: only unsupported ones match (3/10)
        assertEquals(0.3, result.metricSummary().groundingAccuracy(), 0.001);

        // Unsupported detection rate: 3/3 = 1.0
        assertEquals(1.0, result.metricSummary().unsupportedDetectionRate(), 0.001);

        // Overall pass should be false since retrieval_precision < 0.7
        assertFalse(result.overallPass());
    }

    @Test
    void execute_whenDatasetMissing_throws404() {
        // datasetPath was never written, so it doesn't exist
        EvaluationException ex = assertThrows(EvaluationException.class, () -> runner.execute());
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void execute_whenDatasetMalformed_throws404() throws IOException {
        Files.writeString(datasetPath, "this is not valid json");

        EvaluationException ex = assertThrows(EvaluationException.class, () -> runner.execute());
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Failed to parse"));
    }

    @Test
    void execute_whenIngestionNotDone_throws409() throws IOException {
        // Create runner with ingestion check returning false
        AppConfig appConfig = new AppConfig(
                5, 0.7, 4000, 10, 30, 20, 10, 60, 30, 512, 50,
                0.7, 120, 5, 5, 30, "local", "gpt-4o-mini",
                "text-embedding-3-small", "http://localhost:8000",
                "http://localhost:9000", "./data/knowledge-base",
                datasetPath.toString(), reportPath.toString(), 30,
                "http://localhost:5173"
        );

        DefaultEvaluationRunner runnerNoIngestion = new DefaultEvaluationRunner(
                appConfig, chunkRetriever, objectMapper, () -> false);

        Files.writeString(datasetPath, "[]");

        EvaluationException ex = assertThrows(EvaluationException.class, runnerNoIngestion::execute);
        assertEquals(409, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Ingestion must be completed"));
    }

    @Test
    void execute_perExampleResults_containsCorrectFields() throws IOException {
        String dataset = """
                [
                  {"question": "How to reset password?", "expectedCategory": "answerable", "expectedAnswerKeywords": ["reset", "password"]},
                  {"question": "What is dark matter?", "expectedCategory": "unsupported", "expectedAnswerKeywords": []}
                ]
                """;
        Files.writeString(datasetPath, dataset);

        ChunkMetadata metadata = new ChunkMetadata(
                "guide.md", "guide.md", ".md", Instant.now(), "Guide");

        when(chunkRetriever.retrieve(eq("How to reset password?"), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("Reset your password in settings", 0.9, metadata)));
        when(chunkRetriever.retrieve(eq("What is dark matter?"), anyInt(), anyDouble()))
                .thenReturn(List.of());

        EvaluationResult result = runner.execute();

        assertEquals(2, result.perExampleResults().size());

        ExampleResult first = result.perExampleResults().get(0);
        assertEquals("How to reset password?", first.question());
        assertEquals("answerable", first.expectedCategory());
        assertEquals("answerable", first.actualCategory());
        assertTrue(first.pass());

        ExampleResult second = result.perExampleResults().get(1);
        assertEquals("What is dark matter?", second.question());
        assertEquals("unsupported", second.expectedCategory());
        assertEquals("unsupported", second.actualCategory());
        assertTrue(second.pass());
    }

    @Test
    void execute_persistsReportToConfiguredPath() throws IOException {
        String dataset = """
                [
                  {"question": "Test question", "expectedCategory": "answerable", "expectedAnswerKeywords": ["test"]}
                ]
                """;
        Files.writeString(datasetPath, dataset);

        ChunkMetadata metadata = new ChunkMetadata(
                "test.md", "test.md", ".md", Instant.now(), "Test");
        when(chunkRetriever.retrieve(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievedChunk("Test content with test keyword", 0.9, metadata)));

        EvaluationResult result = runner.execute();

        assertEquals(reportPath.toString(), result.reportPath());
        assertTrue(Files.exists(reportPath));

        // Verify report content is valid JSON with expected structure
        String reportContent = Files.readString(reportPath);
        EvaluationResult parsedReport = objectMapper.readValue(reportContent, EvaluationResult.class);
        assertNotNull(parsedReport.metricSummary());
        assertNotNull(parsedReport.perExampleResults());
    }
}
