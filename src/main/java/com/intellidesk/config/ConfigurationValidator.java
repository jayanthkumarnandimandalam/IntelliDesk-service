package com.intellidesk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates required configuration at startup and logs the active profile
 * and configuration source. Fails fast with descriptive error messages
 * if required properties are missing.
 *
 * Requirements: 9.5, 9.6, 9.7
 */
@Component
public class ConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final IntellideskProperties properties;
    private final Environment environment;

    public ConfigurationValidator(IntellideskProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Validates required configuration properties at startup.
     * Fails with a descriptive error if any required property is missing or empty.
     *
     * For the "local" profile, only basic properties are required (defaults are acceptable).
     * For "dev" and "prod" profiles, external service URLs must be explicitly configured.
     */
    @PostConstruct
    public void validate() {
        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = (activeProfiles.length > 0) ? activeProfiles[0] : "local";

        List<String> missingProperties = new ArrayList<>();

        // Always required: LLM model must be set
        if (isBlank(properties.getLlm().getModel())) {
            missingProperties.add("intellidesk.llm.model");
        }

        // Always required: embedding model must be set
        if (isBlank(properties.getEmbedding().getModel())) {
            missingProperties.add("intellidesk.embedding.model");
        }

        // Profile-specific validation: dev and prod require external service URLs
        if ("dev".equals(activeProfile) || "prod".equals(activeProfile)) {
            if (isBlank(properties.getVectorStore().getUrl())) {
                missingProperties.add("intellidesk.vector-store.url (VECTOR_STORE_URL)");
            }
            if (isBlank(properties.getStt().getUrl())) {
                missingProperties.add("intellidesk.stt.url (STT_SERVICE_URL)");
            }
            if (isBlank(properties.getSecurity().getCorsAllowedOrigins())) {
                missingProperties.add("intellidesk.security.cors-allowed-origins (CORS_ALLOWED_ORIGINS)");
            }
        }

        // Prod-specific: knowledge base directory and evaluation paths must be explicit
        if ("prod".equals(activeProfile)) {
            if (isBlank(properties.getRag().getKnowledgeBaseDir())) {
                missingProperties.add("intellidesk.rag.knowledge-base-dir (KNOWLEDGE_BASE_DIR)");
            }
            if (isBlank(properties.getEvaluation().getDatasetPath())) {
                missingProperties.add("intellidesk.evaluation.dataset-path (EVALUATION_DATASET_PATH)");
            }
            if (isBlank(properties.getEvaluation().getReportOutputPath())) {
                missingProperties.add("intellidesk.evaluation.report-output-path (EVALUATION_REPORT_PATH)");
            }
        }

        if (!missingProperties.isEmpty()) {
            for (String prop : missingProperties) {
                log.error("Missing required configuration property: {}", prop);
            }
            throw new IllegalStateException(
                "Application startup failed: missing required configuration properties: " +
                String.join(", ", missingProperties)
            );
        }
    }

    /**
     * Logs active profile and configuration source at startup (without secrets).
     * Called after validation passes and the application context is fully ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logStartupConfiguration() {
        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = (activeProfiles.length > 0) ? activeProfiles[0] : "local";

        String configSource = determineConfigSource(activeProfile);

        log.info("IntelliDesk started with profile: [{}]", activeProfile);
        log.info("Configuration source: {}", configSource);
        log.info("LLM model: {}", properties.getLlm().getModel());
        log.info("Embedding model: {}", properties.getEmbedding().getModel());
        log.info("RAG top-k: {}, similarity threshold: {}", properties.getRag().getTopK(), properties.getRag().getSimilarityThreshold());
        log.info("Session context window: {}, timeout: {} min", properties.getSession().getContextWindowSize(), properties.getSession().getTimeoutMinutes());
        log.info("Rate limit: {} req/min, max message length: {} chars", properties.getSecurity().getRateLimitPerMinute(), properties.getSecurity().getMaxMessageLength());
        log.info("Circuit breaker: failure threshold={}, cooldown={}s", properties.getCircuitBreaker().getFailureThreshold(), properties.getCircuitBreaker().getCooldownSeconds());
        log.info("Workflow node timeout: {}s", properties.getWorkflow().getNodeTimeoutSeconds());
        log.info("Health check timeout: {}s", properties.getHealth().getCheckTimeoutSeconds());
    }

    private String determineConfigSource(String activeProfile) {
        return switch (activeProfile) {
            case "prod" -> "environment variables (secrets from env vars only)";
            case "dev" -> "environment variables and .env file";
            default -> "application.yml with .env file overrides";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
