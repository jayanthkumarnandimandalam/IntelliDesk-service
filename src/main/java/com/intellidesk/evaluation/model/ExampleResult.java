package com.intellidesk.evaluation.model;

/**
 * Result of evaluating a single example from the evaluation dataset.
 */
public record ExampleResult(
        String question,
        String expectedCategory,
        String actualCategory,
        boolean pass
) {}
