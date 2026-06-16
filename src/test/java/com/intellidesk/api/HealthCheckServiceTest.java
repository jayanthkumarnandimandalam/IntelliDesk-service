package com.intellidesk.api;

import com.intellidesk.api.dto.DependencyHealth;
import com.intellidesk.api.dto.HealthResponse;
import com.intellidesk.config.AppConfig;
import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.resilience.CircuitOpenException;
import com.intellidesk.resilience.CircuitState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HealthCheckService.
 * Validates health check aggregation logic, timeout handling, and status computation.
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock
    private CircuitBreaker llmCircuitBreaker;

    @Mock
    private CircuitBreaker vectorStoreCircuitBreaker;

    @Mock
    private CircuitBreaker sttCircuitBreaker;

    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        // Create AppConfig with 5-second health check timeout
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
                "test", // activeProfile
                "gpt-4o-mini", // llmModel
                "text-embedding-3-small", // embeddingModel
                "http://localhost:8000", // vectorStoreUrl
                "http://localhost:8080", // sttUrl
                "./data/knowledge-base", // knowledgeBaseDir
                "./data/evaluation/dataset.json", // evaluationDatasetPath
                "./data/evaluation/report.json", // evaluationReportPath
                30,     // llmTimeoutSeconds
                "http://localhost:3000" // corsAllowedOrigins
        );

        healthCheckService = new HealthCheckService(
                appConfig, llmCircuitBreaker, vectorStoreCircuitBreaker, sttCircuitBreaker);
    }

    @Test
    @DisplayName("All dependencies UP -> overall status UP with HTTP 200")
    void allDependenciesUp_returnsUpStatus() {
        // Arrange: all circuit breakers are CLOSED and execute successfully
        when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(sttCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

        when(llmCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(vectorStoreCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(sttCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // Act
        HealthResponse response = healthCheckService.checkHealth();

        // Assert
        assertEquals("UP", response.overallStatus());
        assertNotNull(response.timestamp());
        assertEquals(3, response.dependencies().size());

        DependencyHealth llmHealth = response.dependencies().get("llm");
        assertEquals("UP", llmHealth.status());
        assertTrue(llmHealth.responseTimeMs() >= 0);
        assertNotNull(llmHealth.lastSuccessfulCheck());
        assertEquals("CLOSED", llmHealth.circuitBreakerState());

        DependencyHealth vectorStoreHealth = response.dependencies().get("vectorStore");
        assertEquals("UP", vectorStoreHealth.status());

        DependencyHealth sttHealth = response.dependencies().get("stt");
        assertEquals("UP", sttHealth.status());
    }

    @Test
    @DisplayName("One dependency DOWN (circuit open) -> overall status DEGRADED with HTTP 503")
    void oneDependencyDown_returnsDegradedStatus() {
        // Arrange: LLM and VectorStore are UP, STT circuit is OPEN
        when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(sttCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);

        when(llmCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(vectorStoreCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // Act
        HealthResponse response = healthCheckService.checkHealth();

        // Assert
        assertEquals("DEGRADED", response.overallStatus());

        DependencyHealth llmHealth = response.dependencies().get("llm");
        assertEquals("UP", llmHealth.status());

        DependencyHealth vectorStoreHealth = response.dependencies().get("vectorStore");
        assertEquals("UP", vectorStoreHealth.status());

        DependencyHealth sttHealth = response.dependencies().get("stt");
        assertEquals("DOWN", sttHealth.status());
        assertEquals("OPEN", sttHealth.circuitBreakerState());
    }

    @Test
    @DisplayName("Dependency execution throws exception -> reported as DOWN")
    void dependencyThrowsException_reportedAsDown() {
        // Arrange: LLM throws CircuitOpenException, others are UP
        when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(sttCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

        when(llmCircuitBreaker.execute(any())).thenThrow(new CircuitOpenException("llm"));
        when(vectorStoreCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(sttCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // Act
        HealthResponse response = healthCheckService.checkHealth();

        // Assert
        assertEquals("DEGRADED", response.overallStatus());

        DependencyHealth llmHealth = response.dependencies().get("llm");
        assertEquals("DOWN", llmHealth.status());

        DependencyHealth vectorStoreHealth = response.dependencies().get("vectorStore");
        assertEquals("UP", vectorStoreHealth.status());

        DependencyHealth sttHealth = response.dependencies().get("stt");
        assertEquals("UP", sttHealth.status());
    }

    @Test
    @DisplayName("Timeout -> dependency reported as DOWN")
    void dependencyTimesOut_reportedAsDown() {
        // Use a 1-second timeout for faster test execution
        AppConfig shortTimeoutConfig = new AppConfig(
                5, 0.7, 4000, 10, 30, 20, 10, 60, 30, 512, 50,
                0.7, 120, 1, 5, 30, "test",
                "gpt-4o-mini", "text-embedding-3-small",
                "http://localhost:8000", "http://localhost:8080",
                "./data/knowledge-base", "./data/evaluation/dataset.json",
                "./data/evaluation/report.json", 30, "http://localhost:3000"
        );

        HealthCheckService serviceWithShortTimeout = new HealthCheckService(
                shortTimeoutConfig, llmCircuitBreaker, vectorStoreCircuitBreaker, sttCircuitBreaker);

        // Arrange: LLM takes too long (simulated by sleeping > timeout)
        when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(sttCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

        when(llmCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            // Sleep longer than the 1-second timeout
            Thread.sleep(3000);
            return "OK";
        });
        when(vectorStoreCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(sttCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // Act
        HealthResponse response = serviceWithShortTimeout.checkHealth();

        // Assert
        assertEquals("DEGRADED", response.overallStatus());

        DependencyHealth llmHealth = response.dependencies().get("llm");
        assertEquals("DOWN", llmHealth.status());
        assertNull(llmHealth.lastSuccessfulCheck());

        DependencyHealth vectorStoreHealth = response.dependencies().get("vectorStore");
        assertEquals("UP", vectorStoreHealth.status());

        DependencyHealth sttHealth = response.dependencies().get("stt");
        assertEquals("UP", sttHealth.status());
    }

    @Test
    @DisplayName("All dependencies DOWN -> overall status DEGRADED")
    void allDependenciesDown_returnsDegradedStatus() {
        // Arrange: all circuits are OPEN
        when(llmCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);
        when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);
        when(sttCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);

        // Act
        HealthResponse response = healthCheckService.checkHealth();

        // Assert
        assertEquals("DEGRADED", response.overallStatus());

        assertEquals("DOWN", response.dependencies().get("llm").status());
        assertEquals("DOWN", response.dependencies().get("vectorStore").status());
        assertEquals("DOWN", response.dependencies().get("stt").status());
    }

    @Test
    @DisplayName("Last successful check is recorded on UP check")
    void lastSuccessfulCheck_recordedOnSuccess() {
        // Arrange
        when(llmCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(vectorStoreCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);
        when(sttCircuitBreaker.getState()).thenReturn(CircuitState.CLOSED);

        when(llmCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(vectorStoreCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(sttCircuitBreaker.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // Act - first check
        HealthResponse firstResponse = healthCheckService.checkHealth();
        assertNotNull(firstResponse.dependencies().get("llm").lastSuccessfulCheck());

        // Now make LLM go down
        when(llmCircuitBreaker.getState()).thenReturn(CircuitState.OPEN);

        // Act - second check
        HealthResponse secondResponse = healthCheckService.checkHealth();

        // The last successful check should still be from the first check
        DependencyHealth llmHealth = secondResponse.dependencies().get("llm");
        assertEquals("DOWN", llmHealth.status());
        assertNotNull(llmHealth.lastSuccessfulCheck()); // preserved from first check
    }
}
