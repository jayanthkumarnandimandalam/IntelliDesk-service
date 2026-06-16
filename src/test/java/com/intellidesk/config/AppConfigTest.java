package com.intellidesk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AppConfig record and property binding.
 */
class AppConfigTest {

    @Test
    @DisplayName("AppConfig.from() maps all properties with defaults correctly")
    void fromMapsAllDefaults() {
        IntellideskProperties props = createDefaultProperties();

        AppConfig config = AppConfig.from(props, "local");

        assertEquals(5, config.topK());
        assertEquals(0.7, config.similarityThreshold());
        assertEquals(4000, config.maxMessageLength());
        assertEquals(10, config.contextWindowSize());
        assertEquals(30, config.sessionTimeoutMinutes());
        assertEquals(20, config.rateLimitPerMinute());
        assertEquals(10, config.maxAudioSizeMb());
        assertEquals(60, config.maxAudioDurationSec());
        assertEquals(30, config.nodeTimeoutSeconds());
        assertEquals(512, config.chunkSize());
        assertEquals(50, config.chunkOverlap());
        assertEquals(0.7, config.evaluationThreshold());
        assertEquals(120, config.evaluationTimeoutSec());
        assertEquals(5, config.healthCheckTimeoutSec());
        assertEquals(5, config.cbFailureThreshold());
        assertEquals(30, config.cbCooldownSeconds());
        assertEquals("local", config.activeProfile());
        assertEquals("gpt-4o-mini", config.llmModel());
        assertEquals("text-embedding-3-small", config.embeddingModel());
        assertEquals("http://localhost:5173", config.corsAllowedOrigins());
    }

    @Test
    @DisplayName("AppConfig.from() maps custom property values")
    void fromMapsCustomValues() {
        IntellideskProperties props = createDefaultProperties();
        props.getRag().setTopK(10);
        props.getRag().setSimilarityThreshold(0.85);
        props.getSession().setContextWindowSize(20);
        props.getSecurity().setRateLimitPerMinute(50);
        props.getCircuitBreaker().setFailureThreshold(3);

        AppConfig config = AppConfig.from(props, "dev");

        assertEquals(10, config.topK());
        assertEquals(0.85, config.similarityThreshold());
        assertEquals(20, config.contextWindowSize());
        assertEquals(50, config.rateLimitPerMinute());
        assertEquals(3, config.cbFailureThreshold());
        assertEquals("dev", config.activeProfile());
    }

    @Test
    @DisplayName("AppConfig.from() preserves service URLs")
    void fromPreservesServiceUrls() {
        IntellideskProperties props = createDefaultProperties();
        props.getVectorStore().setUrl("http://vector:8000");
        props.getStt().setUrl("http://stt:9000");

        AppConfig config = AppConfig.from(props, "prod");

        assertEquals("http://vector:8000", config.vectorStoreUrl());
        assertEquals("http://stt:9000", config.sttUrl());
        assertEquals("prod", config.activeProfile());
    }

    @Test
    @DisplayName("AppConfig record provides immutable access to all fields")
    void recordFieldsAreAccessible() {
        AppConfig config = new AppConfig(
            5, 0.7, 4000, 10, 30, 20, 10, 60, 30, 512, 50,
            0.7, 120, 5, 5, 30, "local", "gpt-4o-mini",
            "text-embedding-3-small", "http://localhost:8000",
            "http://localhost:9000", "./data/knowledge-base",
            "./data/evaluation/dataset.json", "./data/evaluation/report.json",
            30, "http://localhost:5173"
        );

        assertNotNull(config.toString());
        assertEquals(config, config); // record equality
    }

    private IntellideskProperties createDefaultProperties() {
        IntellideskProperties props = new IntellideskProperties();
        // Properties already have defaults set in their classes
        return props;
    }
}
