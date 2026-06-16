package com.intellidesk.evaluation.model;

import java.util.List;

/**
 * A single evaluation example from the evaluation dataset.
 */
public record EvaluationExample(
        String question,
        String expectedCategory,
        List<String> expectedAnswerKeywords
) {}
