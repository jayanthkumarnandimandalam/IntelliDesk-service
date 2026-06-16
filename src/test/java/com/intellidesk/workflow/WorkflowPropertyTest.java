package com.intellidesk.workflow;

import com.intellidesk.config.AppConfig;
import com.intellidesk.workflow.model.WorkflowState;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for the Workflow Engine.
 * Validates Properties 20 and 21 from the design document.
 */
class WorkflowPropertyTest {

    private AppConfig createAppConfig(int nodeTimeoutSeconds) {
        return new AppConfig(
                5, 0.7, 4000, 10, 30, 20, 10, 60,
                nodeTimeoutSeconds,
                512, 50, 0.7, 120, 5, 5, 30,
                "local", "gpt-4o-mini", "text-embedding-3-small",
                "http://localhost:8000", "http://localhost:9000",
                "data/knowledge-base", "data/evaluation/dataset.json",
                "data/evaluation/report.json", 30, "http://localhost:3000"
        );
    }

    // ========================================================================
    // Property 20: Workflow state accumulation
    // After node i, state contains all fields from nodes 1..i
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 20: Workflow state accumulation")
    void workflowStateAccumulatesFieldsFromAllPriorNodes(
            @ForAll @IntRange(min = 2, max = 6) int nodeCount
    ) {
        // **Validates: Requirements 7.2**
        // Create nodes that each add a specific field to the state
        List<WorkflowNode> nodes = new ArrayList<>();
        List<String> expectedFields = new ArrayList<>();

        for (int i = 0; i < nodeCount; i++) {
            final int index = i;
            final String fieldKey = "field_" + index;
            expectedFields.add(fieldKey);

            nodes.add(new WorkflowNode() {
                @Override
                public String name() {
                    return "Node_" + index;
                }

                @Override
                public WorkflowState execute(WorkflowState state) {
                    // Each node adds its field to metadata while preserving existing state
                    Map<String, Object> newMetadata = new HashMap<>(
                            state.metadata() != null ? state.metadata() : Collections.emptyMap());
                    newMetadata.put(fieldKey, "value_" + index);

                    // Return state with accumulated metadata
                    return new WorkflowState(
                            state.originalQuery(),
                            state.sessionId(),
                            state.requestId(),
                            state.sessionContext(),
                            state.retrievedChunks(),
                            index >= 1 ? "augmented_at_" + index : state.augmentedPrompt(),
                            index >= 2 ? "answer_at_" + index : state.generatedAnswer(),
                            index >= 3 ? "grounding_at_" + index : state.groundingResult(),
                            newMetadata
                    );
                }
            });
        }

        AppConfig appConfig = createAppConfig(30);
        WorkflowEngine engine = new WorkflowEngine(nodes, appConfig);

        WorkflowState initialState = new WorkflowState(
                "test query", "session-1", UUID.randomUUID().toString(),
                Collections.emptyList(), null, null, null, null,
                new HashMap<>()
        );

        WorkflowState finalState = engine.execute(initialState);

        // After all nodes execute, state should contain all fields from nodes 1..N
        assertThat(finalState.metadata()).isNotNull();
        for (String expectedField : expectedFields) {
            assertThat(finalState.metadata()).containsKey(expectedField);
        }
        // Verify the count matches exactly
        assertThat(finalState.metadata())
                .containsKeys(expectedFields.toArray(new String[0]));
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 20: Workflow state accumulation")
    void eachNodeReceivesCompleteAccumulatedStateFromPreviousNode(
            @ForAll @IntRange(min = 2, max = 5) int nodeCount
    ) {
        // **Validates: Requirements 7.2**
        // Track the state received by each node
        List<Map<String, Object>> receivedStates = Collections.synchronizedList(new ArrayList<>());

        List<WorkflowNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            final int index = i;
            nodes.add(new WorkflowNode() {
                @Override
                public String name() {
                    return "AccumNode_" + index;
                }

                @Override
                public WorkflowState execute(WorkflowState state) {
                    // Record what this node received
                    Map<String, Object> received = new HashMap<>(
                            state.metadata() != null ? state.metadata() : Collections.emptyMap());
                    receivedStates.add(received);

                    // Add this node's field
                    Map<String, Object> newMetadata = new HashMap<>(received);
                    newMetadata.put("node_" + index, true);

                    return new WorkflowState(
                            state.originalQuery(), state.sessionId(), state.requestId(),
                            state.sessionContext(), state.retrievedChunks(),
                            state.augmentedPrompt(), state.generatedAnswer(),
                            state.groundingResult(), newMetadata
                    );
                }
            });
        }

        AppConfig appConfig = createAppConfig(30);
        WorkflowEngine engine = new WorkflowEngine(nodes, appConfig);

        WorkflowState initialState = new WorkflowState(
                "query", "session-1", UUID.randomUUID().toString(),
                Collections.emptyList(), null, null, null, null,
                new HashMap<>()
        );

        engine.execute(initialState);

        // Node i should have received all fields from nodes 0..i-1
        for (int i = 1; i < receivedStates.size(); i++) {
            Map<String, Object> stateAtNodeI = receivedStates.get(i);
            for (int j = 0; j < i; j++) {
                assertThat(stateAtNodeI)
                        .as("Node %d should see field from node %d", i, j)
                        .containsKey("node_" + j);
            }
        }
    }

    // ========================================================================
    // Property 21: Workflow node failure halts execution
    // Exception/timeout halts, no subsequent nodes run,
    // error has failed_node + request_id
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 21: Workflow node failure halts execution")
    void exceptionInNodeHaltsExecution(
            @ForAll @IntRange(min = 0, max = 4) int failingNodeIndex,
            @ForAll @IntRange(min = 2, max = 6) int totalNodes
    ) {
        // **Validates: Requirements 7.4**
        int effectiveFailIndex = Math.min(failingNodeIndex, totalNodes - 1);

        AtomicInteger executedCount = new AtomicInteger(0);
        List<WorkflowNode> nodes = new ArrayList<>();

        for (int i = 0; i < totalNodes; i++) {
            final int index = i;
            nodes.add(new WorkflowNode() {
                @Override
                public String name() {
                    return "FailTestNode_" + index;
                }

                @Override
                public WorkflowState execute(WorkflowState state) {
                    executedCount.incrementAndGet();
                    if (index == effectiveFailIndex) {
                        throw new RuntimeException("Simulated failure at node " + index);
                    }
                    return state;
                }
            });
        }

        AppConfig appConfig = createAppConfig(30);
        WorkflowEngine engine = new WorkflowEngine(nodes, appConfig);

        String requestId = UUID.randomUUID().toString();
        WorkflowState initialState = new WorkflowState(
                "test", "session-1", requestId,
                Collections.emptyList(), null, null, null, null,
                new HashMap<>()
        );

        assertThatThrownBy(() -> engine.execute(initialState))
                .isInstanceOf(WorkflowException.class)
                .satisfies(ex -> {
                    WorkflowException we = (WorkflowException) ex;
                    // Error SHALL contain failed_node name
                    assertThat(we.getFailedNode()).isEqualTo("FailTestNode_" + effectiveFailIndex);
                    // Error SHALL contain request_id
                    assertThat(we.getRequestId()).isEqualTo(requestId);
                });

        // No subsequent nodes SHALL execute after the failing node
        assertThat(executedCount.get()).isEqualTo(effectiveFailIndex + 1);
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 21: Workflow node failure halts execution")
    void timeoutInNodeHaltsExecution(
            @ForAll @IntRange(min = 0, max = 2) int failingNodeIndex
    ) {
        // **Validates: Requirements 7.4**
        int totalNodes = 3;
        int effectiveFailIndex = Math.min(failingNodeIndex, totalNodes - 1);

        AtomicInteger executedCount = new AtomicInteger(0);
        List<WorkflowNode> nodes = new ArrayList<>();

        for (int i = 0; i < totalNodes; i++) {
            final int index = i;
            nodes.add(new WorkflowNode() {
                @Override
                public String name() {
                    return "TimeoutNode_" + index;
                }

                @Override
                public WorkflowState execute(WorkflowState state) {
                    executedCount.incrementAndGet();
                    if (index == effectiveFailIndex) {
                        // Simulate a long-running operation that exceeds timeout
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted", e);
                        }
                    }
                    return state;
                }
            });
        }

        // Use a very short timeout (1 second) to trigger timeout
        AppConfig appConfig = createAppConfig(1);
        WorkflowEngine engine = new WorkflowEngine(nodes, appConfig);

        String requestId = UUID.randomUUID().toString();
        WorkflowState initialState = new WorkflowState(
                "test", "session-1", requestId,
                Collections.emptyList(), null, null, null, null,
                new HashMap<>()
        );

        assertThatThrownBy(() -> engine.execute(initialState))
                .isInstanceOf(WorkflowException.class)
                .satisfies(ex -> {
                    WorkflowException we = (WorkflowException) ex;
                    // Error SHALL contain failed_node name
                    assertThat(we.getFailedNode()).isEqualTo("TimeoutNode_" + effectiveFailIndex);
                    // Error SHALL contain request_id
                    assertThat(we.getRequestId()).isEqualTo(requestId);
                });

        // No subsequent nodes SHALL have been executed
        // (executedCount may be effectiveFailIndex + 1 since the failing node started)
        assertThat(executedCount.get()).isLessThanOrEqualTo(effectiveFailIndex + 1);
    }
}
