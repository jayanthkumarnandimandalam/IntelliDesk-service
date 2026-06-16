package com.intellidesk.evaluation;

import com.intellidesk.evaluation.model.EvaluationResult;

/**
 * Interface for the offline evaluation runner.
 * Executes evaluation against a curated dataset and produces metrics.
 */
public interface EvaluationRunner {

    /**
     * Executes the evaluation dataset and computes metrics.
     *
     * @return the evaluation result containing metrics, overall pass/fail, and per-example results
     * @throws EvaluationException if evaluation cannot be completed
     */
    EvaluationResult execute();
}
