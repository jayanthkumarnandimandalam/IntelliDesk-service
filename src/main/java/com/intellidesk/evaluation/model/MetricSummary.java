package com.intellidesk.evaluation.model;

/**
 * Summary of evaluation metrics across all examples.
 */
public record MetricSummary(
        double retrievalPrecision,
        double answerRelevance,
        double groundingAccuracy,
        double unsupportedDetectionRate
) {}
