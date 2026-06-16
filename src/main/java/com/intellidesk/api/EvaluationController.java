package com.intellidesk.api;

import com.intellidesk.config.AppConfig;
import com.intellidesk.evaluation.EvaluationException;
import com.intellidesk.evaluation.EvaluationRunner;
import com.intellidesk.evaluation.model.EvaluationResult;
import com.intellidesk.rag.InMemoryVectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REST controller for offline evaluation operations.
 * Runs the evaluation dataset and returns computed metrics.
 */
@RestController
@RequestMapping("/api")
public class EvaluationController {

    private final EvaluationRunner evaluationRunner;
    private final InMemoryVectorStore vectorStore;
    private final AppConfig appConfig;
    private final AtomicBoolean evaluationInProgress = new AtomicBoolean(false);

    public EvaluationController(EvaluationRunner evaluationRunner,
                                InMemoryVectorStore vectorStore,
                                AppConfig appConfig) {
        this.evaluationRunner = evaluationRunner;
        this.vectorStore = vectorStore;
        this.appConfig = appConfig;
    }

    /**
     * POST /api/evaluate - Run offline evaluation against the curated dataset.
     *
     * @return 200 with EvaluationResult on success,
     *         404 if dataset is missing,
     *         409 if ingestion not done or evaluation already running,
     *         504 if evaluation times out
     */
    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate() {
        // Check if knowledge base has been ingested
        if (vectorStore.size() == 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Ingestion must be completed before running evaluation",
                            "status", 409
                    ));
        }

        // Check if evaluation is already in progress
        if (!evaluationInProgress.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Evaluation already in progress",
                            "status", 409
                    ));
        }

        try {
            EvaluationResult result = executeWithTimeout();
            return ResponseEntity.ok(result);

        } catch (EvaluationException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", e.getMessage(), "status", e.getStatusCode()));

        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of(
                            "error", "Evaluation timed out after " + appConfig.evaluationTimeoutSec() + " seconds",
                            "status", 504
                    ));
        } finally {
            evaluationInProgress.set(false);
        }
    }

    private EvaluationResult executeWithTimeout() throws TimeoutException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<EvaluationResult> task = evaluationRunner::execute;
            Future<EvaluationResult> future = executor.submit(task);
            return future.get(appConfig.evaluationTimeoutSec(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof EvaluationException evalEx) {
                throw evalEx;
            }
            throw new EvaluationException("Evaluation failed: " + cause.getMessage(), 500, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvaluationException("Evaluation interrupted", 500, e);
        } finally {
            executor.shutdownNow();
        }
    }
}
