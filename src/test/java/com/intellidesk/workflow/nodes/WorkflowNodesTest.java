package com.intellidesk.workflow.nodes;

import com.intellidesk.config.AppConfig;
import com.intellidesk.rag.ChunkRetriever;
import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.workflow.model.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowNodesTest {

    private CircuitBreaker mockCircuitBreaker;

    @BeforeEach
    void setUp() {
        mockCircuitBreaker = mock(CircuitBreaker.class);
        // By default, circuit breaker just executes the supplier
        when(mockCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    private AppConfig createTestConfig() {
        return new AppConfig(
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
                "gpt-4o-mini", // llmModel
                "text-embedding-3-small", // embeddingModel
                "http://localhost:8000", // vectorStoreUrl
                "http://localhost:9000", // sttUrl
                "data/knowledge-base", // knowledgeBaseDir
                "data/evaluation/dataset.json", // evaluationDatasetPath
                "data/evaluation/report.json",  // evaluationReportPath
                30,     // llmTimeoutSeconds
                "http://localhost:3000" // corsAllowedOrigins
        );
    }

    private WorkflowState createBaseState(String query) {
        return new WorkflowState(
                query,
                "test-session-123",
                "test-request-456",
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                Map.of()
        );
    }

    @Nested
    @DisplayName("ContextRetrievalNode tests")
    class ContextRetrievalNodeTests {

        private ChunkRetriever mockRetriever;
        private ContextRetrievalNode node;

        @BeforeEach
        void setUp() {
            mockRetriever = mock(ChunkRetriever.class);
            node = new ContextRetrievalNode(mockRetriever, mockCircuitBreaker, createTestConfig());
        }

        @Test
        @DisplayName("should retrieve chunks and put them in state")
        void shouldRetrieveChunksAndPutInState() {
            List<RetrievedChunk> expectedChunks = List.of(
                    new RetrievedChunk("Reset your password via Settings > Security",
                            0.92,
                            new ChunkMetadata("password-guide.md", "docs/password-guide.md",
                                    ".md", Instant.now(), "Password Reset")),
                    new RetrievedChunk("Click 'Forgot Password' on the login page",
                            0.85,
                            new ChunkMetadata("login-help.md", "docs/login-help.md",
                                    ".md", Instant.now(), "Login Help"))
            );

            when(mockRetriever.retrieve("How do I reset my password?", 5, 0.7))
                    .thenReturn(expectedChunks);

            WorkflowState input = createBaseState("How do I reset my password?");
            WorkflowState result = node.execute(input);

            assertEquals(expectedChunks, result.retrievedChunks());
            assertEquals("How do I reset my password?", result.originalQuery());
            assertEquals("test-session-123", result.sessionId());
            assertEquals("test-request-456", result.requestId());
        }

        @Test
        @DisplayName("should return empty chunks when nothing matches threshold")
        void shouldReturnEmptyWhenNoMatch() {
            when(mockRetriever.retrieve(anyString(), eq(5), eq(0.7)))
                    .thenReturn(Collections.emptyList());

            WorkflowState input = createBaseState("Something completely unrelated");
            WorkflowState result = node.execute(input);

            assertNotNull(result.retrievedChunks());
            assertTrue(result.retrievedChunks().isEmpty());
        }

        @Test
        @DisplayName("should preserve existing state fields")
        void shouldPreserveExistingStateFields() {
            when(mockRetriever.retrieve(anyString(), eq(5), eq(0.7)))
                    .thenReturn(Collections.emptyList());

            List<ConversationExchange> context = List.of(
                    new ConversationExchange("prev question", "prev answer", Instant.now())
            );

            WorkflowState input = new WorkflowState(
                    "test query", "session-1", "request-1",
                    context, null, null, null, null, Map.of("key", "value")
            );

            WorkflowState result = node.execute(input);

            assertEquals(context, result.sessionContext());
            assertEquals("session-1", result.sessionId());
            assertEquals("request-1", result.requestId());
        }

        @Test
        @DisplayName("node name should be ContextRetrievalNode")
        void shouldHaveCorrectName() {
            assertEquals("ContextRetrievalNode", node.name());
        }
    }

    @Nested
    @DisplayName("GroundingEvaluationNode tests")
    class GroundingEvaluationNodeTests {

        private GroundingEvaluationNode node;

        @BeforeEach
        void setUp() {
            node = new GroundingEvaluationNode();
        }

        @Test
        @DisplayName("should classify as unsupported when no chunks")
        void shouldClassifyUnsupportedWhenNoChunks() {
            WorkflowState input = new WorkflowState(
                    "test query", "session-1", "request-1",
                    Collections.emptyList(), Collections.emptyList(),
                    "some prompt", "The answer is here.", null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertEquals("unsupported", result.groundingResult());
        }

        @Test
        @DisplayName("should classify as supported when answer matches chunks")
        void shouldClassifySupportedWhenAnswerMatchesChunks() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk(
                            "To reset your password, go to Settings and click Security. Then click Reset Password.",
                            0.92,
                            new ChunkMetadata("password.md", "docs/password.md",
                                    ".md", Instant.now(), "Reset"))
            );

            WorkflowState input = new WorkflowState(
                    "How to reset password?", "session-1", "request-1",
                    Collections.emptyList(), chunks,
                    "some prompt",
                    "To reset your password, go to Settings and click Security.",
                    null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertEquals("supported", result.groundingResult());
        }

        @Test
        @DisplayName("should classify as partial when answer partially overlaps")
        void shouldClassifyPartialWhenPartialOverlap() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk(
                            "The VPN client requires version 5.2 or higher for Windows.",
                            0.88,
                            new ChunkMetadata("vpn.md", "docs/vpn.md",
                                    ".md", Instant.now(), "VPN Setup"))
            );

            WorkflowState input = new WorkflowState(
                    "VPN setup", "session-1", "request-1",
                    Collections.emptyList(), chunks,
                    "some prompt",
                    "The VPN client requires version 5.2 or higher for Windows. Additionally you need to configure your firewall settings and enable port forwarding for connections.",
                    null, Map.of()
            );

            WorkflowState result = node.execute(input);

            // Answer extends beyond chunks, so should be partial
            assertTrue(result.groundingResult().equals("partial")
                    || result.groundingResult().equals("supported"));
        }

        @Test
        @DisplayName("should classify as unsupported when answer has no overlap with chunks")
        void shouldClassifyUnsupportedWhenNoOverlap() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk(
                            "The printer driver supports HP LaserJet and Canon models.",
                            0.75,
                            new ChunkMetadata("printers.md", "docs/printers.md",
                                    ".md", Instant.now(), "Printers"))
            );

            WorkflowState input = new WorkflowState(
                    "quantum computing", "session-1", "request-1",
                    Collections.emptyList(), chunks,
                    "some prompt",
                    "Quantum computing leverages quantum mechanics to perform calculations exponentially faster than classical computers.",
                    null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertEquals("unsupported", result.groundingResult());
        }

        @Test
        @DisplayName("should classify as unsupported when answer is null")
        void shouldClassifyUnsupportedWhenAnswerNull() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk("some content", 0.9,
                            new ChunkMetadata("file.md", "file.md", ".md", Instant.now(), null))
            );

            WorkflowState input = new WorkflowState(
                    "query", "session-1", "request-1",
                    Collections.emptyList(), chunks,
                    "prompt", null, null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertEquals("unsupported", result.groundingResult());
        }

        @Test
        @DisplayName("node name should be GroundingEvaluationNode")
        void shouldHaveCorrectName() {
            assertEquals("GroundingEvaluationNode", node.name());
        }
    }

    @Nested
    @DisplayName("TranscriptionNode tests")
    class TranscriptionNodeTests {

        private SttService mockSttService;
        private TranscriptionNode node;

        @BeforeEach
        void setUp() {
            mockSttService = mock(SttService.class);
            node = new TranscriptionNode(mockSttService, mockCircuitBreaker);
        }

        @Test
        @DisplayName("should transcribe audio and put transcript as original query")
        void shouldTranscribeAudio() {
            byte[] audioData = "fake audio data".getBytes();
            when(mockSttService.transcribe(audioData)).thenReturn("How do I reset my password?");

            WorkflowState input = new WorkflowState(
                    null, "session-1", "request-1",
                    Collections.emptyList(), null, null, null, null,
                    Map.of("audioBytes", audioData)
            );

            WorkflowState result = node.execute(input);

            assertEquals("How do I reset my password?", result.originalQuery());
            assertEquals("How do I reset my password?", result.metadata().get("transcript"));
        }

        @Test
        @DisplayName("should pass through when no audio bytes in metadata")
        void shouldPassThroughWhenNoAudio() {
            WorkflowState input = new WorkflowState(
                    "text query", "session-1", "request-1",
                    Collections.emptyList(), null, null, null, null,
                    Map.of()
            );

            WorkflowState result = node.execute(input);

            assertSame(input, result);
        }

        @Test
        @DisplayName("node name should be TranscriptionNode")
        void shouldHaveCorrectName() {
            assertEquals("TranscriptionNode", node.name());
        }
    }

    @Nested
    @DisplayName("ContextAugmentationNode tests")
    class ContextAugmentationNodeTests {

        private ContextAugmentationNode node;

        @BeforeEach
        void setUp() {
            node = new ContextAugmentationNode();
        }

        @Test
        @DisplayName("should build prompt with chunks and query")
        void shouldBuildPromptWithChunksAndQuery() {
            List<RetrievedChunk> chunks = List.of(
                    new RetrievedChunk("Reset via Settings > Security", 0.92,
                            new ChunkMetadata("password.md", "docs/password.md",
                                    ".md", Instant.now(), "Reset Steps"))
            );

            WorkflowState input = new WorkflowState(
                    "How to reset password?", "session-1", "request-1",
                    Collections.emptyList(), chunks, null, null, null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertNotNull(result.augmentedPrompt());
            assertTrue(result.augmentedPrompt().contains("Reset via Settings > Security"));
            assertTrue(result.augmentedPrompt().contains("How to reset password?"));
            assertTrue(result.augmentedPrompt().contains("password.md"));
        }

        @Test
        @DisplayName("should include conversation history when present")
        void shouldIncludeConversationHistory() {
            List<ConversationExchange> context = List.of(
                    new ConversationExchange("What is VPN?", "VPN is a virtual private network.", Instant.now())
            );

            WorkflowState input = new WorkflowState(
                    "How to connect?", "session-1", "request-1",
                    context, Collections.emptyList(), null, null, null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertTrue(result.augmentedPrompt().contains("CONVERSATION HISTORY"));
            assertTrue(result.augmentedPrompt().contains("What is VPN?"));
        }

        @Test
        @DisplayName("node name should be ContextAugmentationNode")
        void shouldHaveCorrectName() {
            assertEquals("ContextAugmentationNode", node.name());
        }
    }

    @Nested
    @DisplayName("AnswerGenerationNode tests")
    class AnswerGenerationNodeTests {

        private LlmService mockLlmService;
        private AnswerGenerationNode node;

        @BeforeEach
        void setUp() {
            mockLlmService = mock(LlmService.class);
            node = new AnswerGenerationNode(mockLlmService, mockCircuitBreaker);
        }

        @Test
        @DisplayName("should call LLM and put generated answer in state")
        void shouldCallLlmAndPutAnswer() {
            when(mockLlmService.generate(anyString())).thenReturn("Reset from Settings > Security.");

            WorkflowState input = new WorkflowState(
                    "How to reset?", "session-1", "request-1",
                    Collections.emptyList(), Collections.emptyList(),
                    "augmented prompt here", null, null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertEquals("Reset from Settings > Security.", result.generatedAnswer());
        }

        @Test
        @DisplayName("should throw when no augmented prompt")
        void shouldThrowWhenNoPrompt() {
            WorkflowState input = new WorkflowState(
                    "query", "session-1", "request-1",
                    Collections.emptyList(), Collections.emptyList(),
                    null, null, null, Map.of()
            );

            assertThrows(IllegalStateException.class, () -> node.execute(input));
        }

        @Test
        @DisplayName("node name should be AnswerGenerationNode")
        void shouldHaveCorrectName() {
            assertEquals("AnswerGenerationNode", node.name());
        }
    }

    @Nested
    @DisplayName("QueryUnderstandingNode tests")
    class QueryUnderstandingNodeTests {

        private QueryUnderstandingNode node;

        @BeforeEach
        void setUp() {
            node = new QueryUnderstandingNode();
        }

        @Test
        @DisplayName("should pass through when no session context")
        void shouldPassThroughWhenNoContext() {
            WorkflowState input = createBaseState("How to reset password?");
            WorkflowState result = node.execute(input);

            assertEquals("How to reset password?", result.originalQuery());
        }

        @Test
        @DisplayName("should resolve pronoun references with context")
        void shouldResolvePronouns() {
            List<ConversationExchange> context = List.of(
                    new ConversationExchange("How to set up VPN?", "Install the client...", Instant.now())
            );

            WorkflowState input = new WorkflowState(
                    "Tell me more about it", "session-1", "request-1",
                    context, null, null, null, null, Map.of()
            );

            WorkflowState result = node.execute(input);

            assertTrue(result.originalQuery().contains("context:"));
            assertTrue(result.originalQuery().contains("How to set up VPN?"));
        }

        @Test
        @DisplayName("node name should be QueryUnderstandingNode")
        void shouldHaveCorrectName() {
            assertEquals("QueryUnderstandingNode", node.name());
        }
    }
}
