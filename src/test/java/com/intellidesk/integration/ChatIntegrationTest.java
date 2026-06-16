package com.intellidesk.integration;

import com.intellidesk.api.ChatController;
import com.intellidesk.api.dto.ChatRequest;
import com.intellidesk.api.dto.ChatResponse;
import com.intellidesk.config.AppConfig;
import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.resilience.CircuitState;
import com.intellidesk.resilience.DegradationHandler;
import com.intellidesk.security.InputSanitizer;
import com.intellidesk.session.InMemorySessionManager;
import com.intellidesk.session.SessionManager;
import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.workflow.WorkflowNode;
import com.intellidesk.workflow.model.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the end-to-end chat flow.
 * Uses real InputSanitizer and InMemorySessionManager with mocked LLM/VectorStore
 * via stubbed workflow nodes.
 *
 * Validates: Requirements 11.1, 11.3, 11.4
 */
class ChatIntegrationTest {

    private ChatController chatController;
    private SessionManager sessionManager;
    private DegradationHandler degradationHandler;
    private AppConfig appConfig;

    // Simulated document fixtures
    private static final ChunkMetadata DOC1_META = new ChunkMetadata(
            "password-reset.md", "guides/password-reset.md", ".md",
            Instant.parse("2024-01-10T00:00:00Z"), "Password Reset Guide");
    private static final ChunkMetadata DOC2_META = new ChunkMetadata(
            "vpn-setup.txt", "guides/vpn-setup.txt", ".txt",
            Instant.parse("2024-01-11T00:00:00Z"), "VPN Configuration");
    private static final ChunkMetadata DOC3_META = new ChunkMetadata(
            "email-config.md", "guides/email-config.md", ".md",
            Instant.parse("2024-01-12T00:00:00Z"), "Email Configuration");

    private static final RetrievedChunk CHUNK_PASSWORD = new RetrievedChunk(
            "To reset your password, navigate to Settings > Security > Change Password. Enter your current password and then the new one.",
            0.92, DOC1_META);
    private static final RetrievedChunk CHUNK_VPN = new RetrievedChunk(
            "To set up VPN, install the company VPN client, enter server address vpn.company.com, and use your corporate credentials.",
            0.88, DOC2_META);
    private static final RetrievedChunk CHUNK_EMAIL = new RetrievedChunk(
            "To configure email on mobile, use IMAP server mail.company.com port 993 with SSL enabled.",
            0.85, DOC3_META);

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig(
                5, 0.7, 4000, 10, 30, 20, 10, 60,
                30, 512, 50, 0.7, 120, 5, 5, 30,
                "local", "gpt-4o-mini", "text-embedding-3-small",
                "http://localhost:8000", "http://localhost:9000",
                "data/knowledge-base", "data/evaluation/dataset.json",
                "data/evaluation/report.json", 30, "http://localhost:3000"
        );

        // Real session manager (in-memory)
        sessionManager = new InMemorySessionManager(appConfig);

        // Mock circuit breakers for degradation handler - all CLOSED (healthy)
        CircuitBreaker llmCb = mock(CircuitBreaker.class);
        CircuitBreaker vsCb = mock(CircuitBreaker.class);
        CircuitBreaker sttCb = mock(CircuitBreaker.class);
        when(llmCb.getState()).thenReturn(CircuitState.CLOSED);
        when(vsCb.getState()).thenReturn(CircuitState.CLOSED);
        when(sttCb.getState()).thenReturn(CircuitState.CLOSED);
        degradationHandler = new DegradationHandler(llmCb, vsCb, sttCb);
    }

    private ChatController createControllerWithNode(WorkflowNode node) {
        return new ChatController(
                appConfig,
                new InputSanitizer(),
                sessionManager,
                degradationHandler,
                List.of(node)
        );
    }

    /**
     * Simulates a workflow node that returns relevant chunks and a generated answer.
     */
    private WorkflowNode createAnsweringNode(String query, List<RetrievedChunk> chunks, String answer, String grounding) {
        return new WorkflowNode() {
            @Override
            public String name() { return "MockRAGNode"; }

            @Override
            public WorkflowState execute(WorkflowState state) {
                return new WorkflowState(
                        state.originalQuery(),
                        state.sessionId(),
                        state.requestId(),
                        state.sessionContext(),
                        chunks,
                        "augmented prompt with context",
                        answer,
                        grounding,
                        state.metadata()
                );
            }
        };
    }

    /**
     * Simulates a workflow node that returns no relevant chunks (insufficient context).
     */
    private WorkflowNode createInsufficientContextNode() {
        return new WorkflowNode() {
            @Override
            public String name() { return "MockRAGNode"; }

            @Override
            public WorkflowState execute(WorkflowState state) {
                return new WorkflowState(
                        state.originalQuery(),
                        state.sessionId(),
                        state.requestId(),
                        state.sessionContext(),
                        Collections.emptyList(),
                        null,
                        "I don't have enough information in my knowledge base to answer this question",
                        "unsupported",
                        state.metadata()
                );
            }
        };
    }

    // --- Test fixtures: 3+ documents, 3+ queries ---

    @Test
    @DisplayName("Query 1: Password reset - returns answer with sources from password doc")
    void passwordResetQuery_returnsAnswerWithSources() {
        WorkflowNode node = createAnsweringNode(
                "How do I reset my password?",
                List.of(CHUNK_PASSWORD),
                "To reset your password, navigate to Settings > Security > Change Password.",
                "supported"
        );
        chatController = createControllerWithNode(node);

        String sessionId = UUID.randomUUID().toString();
        ChatRequest request = new ChatRequest(sessionId, "How do I reset my password?");

        ResponseEntity<?> response = chatController.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ChatResponse chatResponse = (ChatResponse) response.getBody();
        assertNotNull(chatResponse);
        assertEquals("supported", chatResponse.groundingNote());
        assertFalse(chatResponse.sources().isEmpty());
        assertEquals("password-reset.md", chatResponse.sources().get(0).documentTitle());
        assertEquals("Password Reset Guide", chatResponse.sources().get(0).section());
        assertTrue(chatResponse.latencyMs() >= 0);
    }

    @Test
    @DisplayName("Query 2: VPN setup - returns answer with sources from VPN doc")
    void vpnSetupQuery_returnsAnswerWithSources() {
        WorkflowNode node = createAnsweringNode(
                "How do I set up VPN?",
                List.of(CHUNK_VPN),
                "Install the company VPN client and connect to vpn.company.com with your credentials.",
                "supported"
        );
        chatController = createControllerWithNode(node);

        String sessionId = UUID.randomUUID().toString();
        ChatRequest request = new ChatRequest(sessionId, "How do I set up VPN?");

        ResponseEntity<?> response = chatController.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ChatResponse chatResponse = (ChatResponse) response.getBody();
        assertNotNull(chatResponse);
        assertEquals("supported", chatResponse.groundingNote());
        assertEquals("vpn-setup.txt", chatResponse.sources().get(0).documentTitle());
    }

    @Test
    @DisplayName("Query 3: Email config - returns answer with sources from email doc")
    void emailConfigQuery_returnsAnswerWithSources() {
        WorkflowNode node = createAnsweringNode(
                "How do I configure email on my phone?",
                List.of(CHUNK_EMAIL),
                "Use IMAP server mail.company.com port 993 with SSL enabled for mobile email setup.",
                "supported"
        );
        chatController = createControllerWithNode(node);

        String sessionId = UUID.randomUUID().toString();
        ChatRequest request = new ChatRequest(sessionId, "How do I configure email on my phone?");

        ResponseEntity<?> response = chatController.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ChatResponse chatResponse = (ChatResponse) response.getBody();
        assertNotNull(chatResponse);
        assertEquals("supported", chatResponse.groundingNote());
        assertEquals("email-config.md", chatResponse.sources().get(0).documentTitle());
    }

    @Test
    @DisplayName("Follow-up question uses session context from previous exchange")
    void followUpQuestion_usesSessionContext() {
        // First, create a node that captures sessionContext to verify it's passed
        List<List<ConversationExchange>> capturedContext = new ArrayList<>();

        WorkflowNode contextCapturingNode = new WorkflowNode() {
            @Override
            public String name() { return "ContextCapturingNode"; }

            @Override
            public WorkflowState execute(WorkflowState state) {
                capturedContext.add(state.sessionContext());
                return new WorkflowState(
                        state.originalQuery(),
                        state.sessionId(),
                        state.requestId(),
                        state.sessionContext(),
                        List.of(CHUNK_PASSWORD),
                        "augmented prompt",
                        "The admin panel is at settings.company.com/admin.",
                        "supported",
                        state.metadata()
                );
            }
        };
        chatController = createControllerWithNode(contextCapturingNode);

        String sessionId = UUID.randomUUID().toString();

        // First message
        ChatRequest firstRequest = new ChatRequest(sessionId, "How do I reset my password?");
        ResponseEntity<?> firstResponse = chatController.chat(firstRequest);
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());

        // Second message (follow-up)
        ChatRequest followUp = new ChatRequest(sessionId, "What about the admin panel?");
        ResponseEntity<?> followUpResponse = chatController.chat(followUp);
        assertEquals(HttpStatus.OK, followUpResponse.getStatusCode());

        // Verify the second call received the first exchange in context
        assertEquals(2, capturedContext.size());
        // First call has empty context (new session)
        assertTrue(capturedContext.get(0).isEmpty());
        // Second call has one exchange from the first message
        assertEquals(1, capturedContext.get(1).size());
        assertEquals("How do I reset my password?", capturedContext.get(1).get(0).userMessage());
    }

    @Test
    @DisplayName("Empty knowledge base returns insufficient context response")
    void emptyKnowledgeBase_returnsInsufficientContextResponse() {
        WorkflowNode node = createInsufficientContextNode();
        chatController = createControllerWithNode(node);

        String sessionId = UUID.randomUUID().toString();
        ChatRequest request = new ChatRequest(sessionId, "What is quantum computing?");

        ResponseEntity<?> response = chatController.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ChatResponse chatResponse = (ChatResponse) response.getBody();
        assertNotNull(chatResponse);
        assertEquals("I don't have enough information in my knowledge base to answer this question",
                chatResponse.answer());
        assertEquals("unsupported", chatResponse.groundingNote());
        assertTrue(chatResponse.sources().isEmpty());
    }

    @Test
    @DisplayName("Multi-document retrieval returns multiple sources")
    void multiDocumentRetrieval_returnsMultipleSources() {
        WorkflowNode node = createAnsweringNode(
                "What are the IT setup steps for a new employee?",
                List.of(CHUNK_PASSWORD, CHUNK_VPN, CHUNK_EMAIL),
                "New employees should: 1) Set up password, 2) Install VPN, 3) Configure email.",
                "supported"
        );
        chatController = createControllerWithNode(node);

        String sessionId = UUID.randomUUID().toString();
        ChatRequest request = new ChatRequest(sessionId, "What are the IT setup steps for a new employee?");

        ResponseEntity<?> response = chatController.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ChatResponse chatResponse = (ChatResponse) response.getBody();
        assertNotNull(chatResponse);
        assertEquals(3, chatResponse.sources().size());
        assertEquals("supported", chatResponse.groundingNote());
    }
}
