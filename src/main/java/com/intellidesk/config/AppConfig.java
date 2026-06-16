package com.intellidesk.config;

/**
 * Immutable record representing the resolved application configuration.
 * Provides a flattened view of all configurable properties with their defaults.
 * Constructed from {@link IntellideskProperties} after binding and validation.
 */
public record AppConfig(
    int topK,
    double similarityThreshold,
    int maxMessageLength,
    int contextWindowSize,
    int sessionTimeoutMinutes,
    int rateLimitPerMinute,
    int maxAudioSizeMb,
    int maxAudioDurationSec,
    int nodeTimeoutSeconds,
    int chunkSize,
    int chunkOverlap,
    double evaluationThreshold,
    int evaluationTimeoutSec,
    int healthCheckTimeoutSec,
    int cbFailureThreshold,
    int cbCooldownSeconds,
    String activeProfile,
    String llmModel,
    String embeddingModel,
    String vectorStoreUrl,
    String sttUrl,
    String knowledgeBaseDir,
    String evaluationDatasetPath,
    String evaluationReportPath,
    int llmTimeoutSeconds,
    String corsAllowedOrigins
) {

    /**
     * Creates an AppConfig from the bound IntellideskProperties and the active Spring profile.
     */
    public static AppConfig from(IntellideskProperties props, String activeProfile) {
        return new AppConfig(
            props.getRag().getTopK(),
            props.getRag().getSimilarityThreshold(),
            props.getSecurity().getMaxMessageLength(),
            props.getSession().getContextWindowSize(),
            props.getSession().getTimeoutMinutes(),
            props.getSecurity().getRateLimitPerMinute(),
            props.getSecurity().getMaxAudioSizeMb(),
            props.getSecurity().getMaxAudioDurationSec(),
            props.getWorkflow().getNodeTimeoutSeconds(),
            props.getRag().getChunkSize(),
            props.getRag().getChunkOverlap(),
            props.getEvaluation().getThreshold(),
            props.getEvaluation().getTimeoutSeconds(),
            props.getHealth().getCheckTimeoutSeconds(),
            props.getCircuitBreaker().getFailureThreshold(),
            props.getCircuitBreaker().getCooldownSeconds(),
            activeProfile,
            props.getLlm().getModel(),
            props.getEmbedding().getModel(),
            props.getVectorStore().getUrl(),
            props.getStt().getUrl(),
            props.getRag().getKnowledgeBaseDir(),
            props.getEvaluation().getDatasetPath(),
            props.getEvaluation().getReportOutputPath(),
            props.getLlm().getTimeoutSeconds(),
            props.getSecurity().getCorsAllowedOrigins()
        );
    }
}
