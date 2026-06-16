package com.intellidesk.evaluation.model;

import java.util.List;

/**
 * Result of an offline evaluation run.
 */
public record EvaluationResult(
        MetricSummary metricSummary,
        boolean overallPass,
        String reportPath,
        List<ExampleResult> perExampleResults
) {}
