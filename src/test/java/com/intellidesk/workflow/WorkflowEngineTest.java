package com.intellidesk.workflow;

import com.intellidesk.config.AppConfig;
import com.intellidesk.workflow.model.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowEngineTest {

    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig(
                5, 0.7, 4000, 10, 30, 20, 10, 60,
                30, // nodeTimeoutSeconds
                512, 50, 0.7, 120, 5, 5, 30,
                "local", "gpt-4o-mini", "text-embedding-3-small",
                "http://localhost:8000", "http://localhost:9000",
                "data/knowledge-base", "data/evaluation/dataset.json",
                "data/evaluation/report.json", 30, "http://localhost:3000"
        );
    }

    private WorkflowState createInitialState() {
        return new WorkflowState(
                "How do I reset my password?",
                "session-123",
                "request-456",
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                new HashMap<>()
        );
    }

    @Test
    @DisplayName("Normal sequential execution - all nodes execute in order")
    void testNormalSequentialExecution() {
        WorkflowNode node1 = new TestNode("node1", state -> state);
        WorkflowNode node2 = new TestNode("node2", state -> state);
        WorkflowNode node3 = new TestNode("node3", state -> state);

        WorkflowEngine engine = new WorkflowEngine(List.of(node1, node2, node3), appConfig);
        WorkflowState initial = createInitialState();

        WorkflowState result = engine.execute(initial);

        assertNotNull(result);
        assertEquals("How do I reset my password?", result.originalQuery());
        assertEquals("session-123", result.sessionId());
        assertEquals("request-456", result.requestId());
    }

    @Test
    @DisplayName("State accumulation - each node adds to state")
    void testStateAccumulation() {
        WorkflowNode node1 = new TestNode("queryUnderstanding", state ->
                new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(), state.retrievedChunks(),
                        "augmented prompt from node1",
                        state.generatedAnswer(), state.groundingResult(), state.metadata()
                )
        );

        WorkflowNode node2 = new TestNode("answerGeneration", state ->
                new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(), state.retrievedChunks(),
                        state.augmentedPrompt(),
                        "Generated answer from node2", state.groundingResult(), state.metadata()
                )
        );

        WorkflowNode node3 = new TestNode("groundingEvaluation", state ->
                new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(), state.retrievedChunks(),
                        state.augmentedPrompt(), state.generatedAnswer(),
                        "supported", state.metadata()
                )
        );

        WorkflowEngine engine = new WorkflowEngine(List.of(node1, node2, node3), appConfig);
        WorkflowState initial = createInitialState();

        WorkflowState result = engine.execute(initial);

        assertEquals("augmented prompt from node1", result.augmentedPrompt());
        assertEquals("Generated answer from node2", result.generatedAnswer());
        assertEquals("supported", result.groundingResult());
    }

    @Test
    @DisplayName("Node failure halts execution - subsequent nodes do not run")
    void testNodeFailureHaltsExecution() {
        TestNode node1 = new TestNode("node1", state -> state);
        WorkflowNode failingNode = new TestNode("failingNode", state -> {
            throw new RuntimeException("Something went wrong");
        });
        TestNode node3 = new TestNode("node3", state -> state);

        WorkflowEngine engine = new WorkflowEngine(List.of(node1, failingNode, node3), appConfig);
        WorkflowState initial = createInitialState();

        WorkflowException exception = assertThrows(WorkflowException.class,
                () -> engine.execute(initial));

        assertEquals("failingNode", exception.getFailedNode());
        assertEquals("request-456", exception.getRequestId());
        assertNotNull(exception.getCause());
        assertTrue(node1.wasExecuted());
        assertFalse(node3.wasExecuted());
    }

    @Test
    @DisplayName("Timeout halts execution when node exceeds configured timeout")
    void testTimeoutHaltsExecution() {
        // Use a very short timeout for testing
        AppConfig shortTimeoutConfig = new AppConfig(
                5, 0.7, 4000, 10, 30, 20, 10, 60,
                1, // 1 second timeout
                512, 50, 0.7, 120, 5, 5, 30,
                "local", "gpt-4o-mini", "text-embedding-3-small",
                "http://localhost:8000", "http://localhost:9000",
                "data/knowledge-base", "data/evaluation/dataset.json",
                "data/evaluation/report.json", 30, "http://localhost:3000"
        );

        WorkflowNode slowNode = new TestNode("slowNode", state -> {
            try {
                Thread.sleep(5000); // 5 seconds, exceeds 1 second timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return state;
        });

        TestNode nodeAfterSlow = new TestNode("nodeAfterSlow", state -> state);
        WorkflowEngine engine = new WorkflowEngine(List.of(slowNode, nodeAfterSlow), shortTimeoutConfig);
        WorkflowState initial = createInitialState();

        WorkflowException exception = assertThrows(WorkflowException.class,
                () -> engine.execute(initial));

        assertEquals("slowNode", exception.getFailedNode());
        assertEquals("request-456", exception.getRequestId());
        assertFalse(nodeAfterSlow.wasExecuted());
    }

    @Test
    @DisplayName("Conditional edge skips remaining nodes when no chunks retrieved")
    void testConditionalEdgeSkipsWhenNoChunks() {
        WorkflowNode contextRetrieval = new TestNode("contextRetrieval", state ->
                new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(),
                        Collections.emptyList(), // empty chunks - no relevant results
                        state.augmentedPrompt(), state.generatedAnswer(),
                        state.groundingResult(), state.metadata()
                )
        );

        TestNode answerGeneration = new TestNode("answerGeneration", state ->
                new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(), state.retrievedChunks(),
                        state.augmentedPrompt(), "This should not appear",
                        state.groundingResult(), state.metadata()
                )
        );

        WorkflowEngine engine = new WorkflowEngine(
                List.of(contextRetrieval, answerGeneration), appConfig);

        // Register conditional edge: if retrievedChunks is empty, short-circuit
        ConditionalEdge insufficientContextEdge = state -> {
            if (state.retrievedChunks() != null && state.retrievedChunks().isEmpty()) {
                return Optional.of(new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(), state.retrievedChunks(),
                        null,
                        "I don't have enough information in my knowledge base to answer this question",
                        "unsupported",
                        Map.of("short_circuited", true, "reason", "insufficient_context")
                ));
            }
            return Optional.empty();
        };
        engine.addConditionalEdge("contextRetrieval", insufficientContextEdge);

        WorkflowState initial = createInitialState();
        WorkflowState result = engine.execute(initial);

        assertEquals("I don't have enough information in my knowledge base to answer this question",
                result.generatedAnswer());
        assertEquals("unsupported", result.groundingResult());
        assertFalse(answerGeneration.wasExecuted());
    }

    @Test
    @DisplayName("Conditional edge allows continuation when chunks are present")
    void testConditionalEdgeAllowsContinuationWithChunks() {
        WorkflowNode contextRetrieval = new TestNode("contextRetrieval", state ->
                new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(),
                        List.of(new com.intellidesk.rag.model.RetrievedChunk("content", 0.85, null)),
                        state.augmentedPrompt(), state.generatedAnswer(),
                        state.groundingResult(), state.metadata()
                )
        );

        TestNode answerGeneration = new TestNode("answerGeneration", state ->
                new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(), state.retrievedChunks(),
                        state.augmentedPrompt(), "Generated answer",
                        state.groundingResult(), state.metadata()
                )
        );

        WorkflowEngine engine = new WorkflowEngine(
                List.of(contextRetrieval, answerGeneration), appConfig);

        // Same conditional edge as above
        ConditionalEdge insufficientContextEdge = state -> {
            if (state.retrievedChunks() != null && state.retrievedChunks().isEmpty()) {
                return Optional.of(new WorkflowState(
                        state.originalQuery(), state.sessionId(), state.requestId(),
                        state.sessionContext(), state.retrievedChunks(),
                        null,
                        "I don't have enough information in my knowledge base to answer this question",
                        "unsupported",
                        Map.of("short_circuited", true)
                ));
            }
            return Optional.empty();
        };
        engine.addConditionalEdge("contextRetrieval", insufficientContextEdge);

        WorkflowState initial = createInitialState();
        WorkflowState result = engine.execute(initial);

        assertEquals("Generated answer", result.generatedAnswer());
        assertTrue(answerGeneration.wasExecuted());
    }

    /**
     * Test node implementation that tracks whether it was executed.
     */
    private static class TestNode implements WorkflowNode {
        private final String name;
        private final java.util.function.Function<WorkflowState, WorkflowState> logic;
        private boolean executed = false;

        TestNode(String name, java.util.function.Function<WorkflowState, WorkflowState> logic) {
            this.name = name;
            this.logic = logic;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public WorkflowState execute(WorkflowState state) {
            executed = true;
            return logic.apply(state);
        }

        boolean wasExecuted() {
            return executed;
        }
    }
}
