package com.intellidesk.api;

import com.intellidesk.rag.IngestionException;
import com.intellidesk.resilience.CircuitOpenException;
import com.intellidesk.workflow.WorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler providing consistent JSON error responses.
 * Handles validation errors, workflow exceptions, circuit breaker exceptions,
 * and ingestion exceptions with appropriate HTTP status codes.
 *
 * <p>Error response format:</p>
 * <pre>
 * {
 *   "error": "Descriptive error message",
 *   "status": 400,
 *   "request_id": "uuid-v4",
 *   "timestamp": "2024-01-15T10:30:00.123Z"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Jakarta Bean Validation failures (e.g., @NotBlank, @Size).
     * Returns HTTP 400 with the first validation error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return buildErrorResponse(errorMessage, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles workflow failures (node exception or timeout).
     * Returns HTTP 500 with the failed node name and request ID.
     */
    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<Map<String, Object>> handleWorkflowException(WorkflowException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Workflow execution failed at node: " + ex.getFailedNode());
        body.put("status", 500);
        body.put("failed_node", ex.getFailedNode());
        body.put("request_id", ex.getRequestId());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Handles circuit breaker open state.
     * Returns HTTP 503 indicating service is temporarily unavailable.
     */
    @ExceptionHandler(CircuitOpenException.class)
    public ResponseEntity<Map<String, Object>> handleCircuitOpenException(CircuitOpenException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Service temporarily unavailable: " + ex.getServiceName());
        body.put("status", 503);
        body.put("request_id", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Handles ingestion-related exceptions with their specific status codes.
     */
    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<Map<String, Object>> handleIngestionException(IngestionException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return buildErrorResponse(ex.getMessage(), status);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Returns HTTP 500 with a generic error message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return buildErrorResponse(
                "Internal server error: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String error, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("status", status.value());
        body.put("request_id", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(status).body(body);
    }
}
